/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import java.util.*

class TransactionResolutionException(val hash: SecureHash) : Exception()
class TransactionConflictException(val conflictRef: StateRef, val tx1: LedgerTransaction, val tx2: LedgerTransaction) : Exception()

/**
 * A TransactionGroup defines a directed acyclic graph of transactions that can be resolved with each other and then
 * verified. Successful verification does not imply the non-existence of other conflicting transactions: simply that
 * this subgraph does not contain conflicts and is accepted by the involved contracts.
 *
 * The inputs of the provided transactions must be resolvable either within the [transactions] set, or from the
 * [nonVerifiedRoots] set. Transactions in the non-verified set are ignored other than for looking up input states.
 */
class TransactionGroup(val transactions: Set<LedgerTransaction>, val nonVerifiedRoots: Set<LedgerTransaction>) {

    /**
     * Verifies the group and returns the set of resolved transactions.
     */
    fun verify(programMap: Map<SecureHash, Contract>): Set<TransactionForVerification> {
        // Check that every input can be resolved to an output.
        // Check that no output is referenced by more than one input.
        // Cycles should be impossible due to the use of hashes as pointers.
        check(transactions.intersect(nonVerifiedRoots).isEmpty())

        val hashToTXMap: Map<SecureHash, List<LedgerTransaction>> = (transactions + nonVerifiedRoots).groupBy { it.hash }
        val refToConsumingTXMap = hashMapOf<StateRef, LedgerTransaction>()

        val resolved = HashSet<TransactionForVerification>(transactions.size)
        for (tx in transactions) {
            val inputs = ArrayList<ContractState>(tx.inStateRefs.size)
            for (ref in tx.inStateRefs) {
                val conflict = refToConsumingTXMap[ref]
                if (conflict != null)
                    throw TransactionConflictException(ref, tx, conflict)
                refToConsumingTXMap[ref] = tx

                // Look up the connecting transaction.
                val ltx = hashToTXMap[ref.txhash]?.single() ?: throw TransactionResolutionException(ref.txhash)
                // Look up the output in that transaction by index.
                inputs.add(ltx.outStates[ref.index])
            }
            resolved.add(TransactionForVerification(inputs, tx.outStates, tx.commands, tx.hash))
        }

        for (tx in resolved)
            tx.verify(programMap)
        return resolved
    }

}

/** A transaction in fully resolved and sig-checked form, ready for passing as input to a verification function. */
data class TransactionForVerification(val inStates: List<ContractState>,
                                      val outStates: List<ContractState>,
                                      val commands: List<AuthenticatedObject<CommandData>>,
                                      val origHash: SecureHash) {
    override fun hashCode() = origHash.hashCode()
    override fun equals(other: Any?) = other is TransactionForVerification && other.origHash == origHash

    /**
     * @throws TransactionVerificationException if a contract throws an exception, the original is in the cause field
     * @throws IllegalStateException if a state refers to an unknown contract.
     */
    @Throws(TransactionVerificationException::class, IllegalStateException::class)
    fun verify(programMap: Map<SecureHash, Contract>) {
        // For each input and output state, locate the program to run. Then execute the verification function. If any
        // throws an exception, the entire transaction is invalid.
        val programHashes = (inStates.map { it.programRef } + outStates.map { it.programRef }).toSet()
        for (hash in programHashes) {
            val program = programMap[hash] ?: throw IllegalStateException("Unknown program hash $hash")
            try {
                program.verify(this)
            } catch(e: Throwable) {
                throw TransactionVerificationException(this, program, e)
            }
        }
    }

    /**
     * Utilities for contract writers to incorporate into their logic.
     */

    data class InOutGroup<T : ContractState>(val inputs: List<T>, val outputs: List<T>)

    // A shortcut to make IDE auto-completion more intuitive for Java users.
    fun getTimestampBy(timestampingAuthority: Party): TimestampCommand? = commands.getTimestampBy(timestampingAuthority)

    // For Java users.
    fun <T : ContractState> groupStates(ofType: Class<T>, selector: (T) -> Any): List<InOutGroup<T>> {
        val inputs = inStates.filterIsInstance(ofType)
        val outputs = outStates.filterIsInstance(ofType)

        val inGroups = inputs.groupBy(selector)
        val outGroups = outputs.groupBy(selector)

        @Suppress("DEPRECATION")
        return groupStatesInternal(inGroups, outGroups)
    }

    // For Kotlin users: this version has nicer syntax and avoids reflection/object creation for the lambda.
    inline fun <reified T : ContractState> groupStates(selector: (T) -> Any): List<InOutGroup<T>> {
        val inputs = inStates.filterIsInstance<T>()
        val outputs = outStates.filterIsInstance<T>()

        val inGroups = inputs.groupBy(selector)
        val outGroups = outputs.groupBy(selector)

        @Suppress("DEPRECATION")
        return groupStatesInternal(inGroups, outGroups)
    }

    @Deprecated("Do not use this directly: exposed as public only due to function inlining")
    fun <T : ContractState> groupStatesInternal(inGroups: Map<Any, List<T>>, outGroups: Map<Any, List<T>>): List<InOutGroup<T>> {
        val result = ArrayList<InOutGroup<T>>()

        for ((k, v) in inGroups.entries)
            result.add(InOutGroup(v, outGroups[k] ?: emptyList()))
        for ((k, v) in outGroups.entries) {
            if (inGroups[k] == null)
                result.add(InOutGroup(emptyList(), v))
        }

        return result
    }
}

/** Thrown if a verification fails due to a contract rejection. */
class TransactionVerificationException(val tx: TransactionForVerification, val contract: Contract, cause: Throwable?) : Exception(cause)