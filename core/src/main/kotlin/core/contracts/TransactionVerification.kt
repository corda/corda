package core.contracts

import core.contracts.*
import core.crypto.Party
import core.crypto.SecureHash
import java.util.*

// TODO: Consider moving this out of the core module and providing a different way for unit tests to test contracts.

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
    fun verify(): Set<TransactionForVerification> {
        // Check that every input can be resolved to an output.
        // Check that no output is referenced by more than one input.
        // Cycles should be impossible due to the use of hashes as pointers.
        check(transactions.intersect(nonVerifiedRoots).isEmpty())

        val hashToTXMap: Map<SecureHash, List<LedgerTransaction>> = (transactions + nonVerifiedRoots).groupBy { it.id }
        val refToConsumingTXMap = hashMapOf<StateRef, LedgerTransaction>()

        val resolved = HashSet<TransactionForVerification>(transactions.size)
        for (tx in transactions) {
            val inputs = ArrayList<ContractState>(tx.inputs.size)
            for (ref in tx.inputs) {
                val conflict = refToConsumingTXMap[ref]
                if (conflict != null)
                    throw TransactionConflictException(ref, tx, conflict)
                refToConsumingTXMap[ref] = tx

                // Look up the connecting transaction.
                val ltx = hashToTXMap[ref.txhash]?.single() ?: throw TransactionResolutionException(ref.txhash)
                // Look up the output in that transaction by index.
                inputs.add(ltx.outputs[ref.index])
            }
            resolved.add(TransactionForVerification(inputs, tx.outputs, tx.attachments, tx.commands, tx.id))
        }

        for (tx in resolved)
            tx.verify()
        return resolved
    }

}

/** A transaction in fully resolved and sig-checked form, ready for passing as input to a verification function. */
data class TransactionForVerification(val inStates: List<ContractState>,
                                      val outStates: List<ContractState>,
                                      val attachments: List<Attachment>,
                                      val commands: List<AuthenticatedObject<CommandData>>,
                                      val origHash: SecureHash) {
    override fun hashCode() = origHash.hashCode()
    override fun equals(other: Any?) = other is TransactionForVerification && other.origHash == origHash

    /**
     * Verifies that the transaction is valid:
     * - Checks that the input states and the timestamp point to the same Notary
     * - Runs the contracts for this transaction. If any contract fails to verify, the whole transaction
     *   is considered to be invalid
     *
     * TODO: Move this out of the core data structure definitions, once unit tests are more cleanly separated.
     *
     * @throws TransactionVerificationException if a contract throws an exception (the original is in the cause field)
     *                                          or the transaction has references to more than one Notary
     */
    @Throws(TransactionVerificationException::class)
    fun verify() {
        verifySingleNotary()
        val contracts = (inStates.map { it.contract } + outStates.map { it.contract }).toSet()
        for (contract in contracts) {
            try {
                contract.verify(this)
            } catch(e: Throwable) {
                throw TransactionVerificationException.ContractRejection(this, contract, e)
            }
        }
    }

    private fun verifySingleNotary() {
        if (inStates.isEmpty()) return
        val notary = inStates.first().notary
        if (inStates.any { it.notary != notary }) throw TransactionVerificationException.MoreThanOneNotary(this)
        val timestampCmd = commands.singleOrNull { it.value is TimestampCommand } ?: return
        if (!timestampCmd.signers.contains(notary.owningKey)) throw TransactionVerificationException.MoreThanOneNotary(this)
    }

    /**
     * Utilities for contract writers to incorporate into their logic.
     */

    /**
     * A set of related inputs and outputs that are connected by some common attributes. An InOutGroup is calculated
     * using [groupStates] and is useful for handling cases where a transaction may contain similar but unrelated
     * state evolutions, for example, a transaction that moves cash in two different currencies. The numbers must add
     * up on both sides of the transaction, but the values must be summed independently per currency. Grouping can
     * be used to simplify this logic.
     */
    data class InOutGroup<T : ContractState, K : Any>(val inputs: List<T>, val outputs: List<T>, val groupingKey: K)

    /** Simply calls [commands.getTimestampBy] as a shortcut to make code completion more intuitive. */
    fun getTimestampBy(timestampingAuthority: Party): TimestampCommand? = commands.getTimestampBy(timestampingAuthority)
    /** Simply calls [commands.getTimestampByName] as a shortcut to make code completion more intuitive. */
    fun getTimestampByName(vararg authorityName: String): TimestampCommand? = commands.getTimestampByName(*authorityName)

    /**
     * Given a type and a function that returns a grouping key, associates inputs and outputs together so that they
     * can be processed as one. The grouping key is any arbitrary object that can act as a map key (so must implement
     * equals and hashCode).
     *
     * The purpose of this function is to simplify the writing of verification logic for transactions that may contain
     * similar but unrelated state evolutions which need to be checked independently. Consider a transaction that
     * simultaneously moves both dollars and euros (e.g. is an atomic FX trade). There may be multiple dollar inputs and
     * multiple dollar outputs, depending on things like how fragmented the owners wallet is and whether various privacy
     * techniques are in use. The quantity of dollars on the output side must sum to the same as on the input side, to
     * ensure no money is being lost track of. This summation and checking must be repeated independently for each
     * currency. To solve this, you would use groupStates with a type of Cash.State and a selector that returns the
     * currency field: the resulting list can then be iterated over to perform the per-currency calculation.
     */
    fun <T : ContractState, K : Any> groupStates(ofType: Class<T>, selector: (T) -> K): List<InOutGroup<T, K>> {
        val inputs = inStates.filterIsInstance(ofType)
        val outputs = outStates.filterIsInstance(ofType)

        val inGroups: Map<K, List<T>> = inputs.groupBy(selector)
        val outGroups: Map<K, List<T>> = outputs.groupBy(selector)

        @Suppress("DEPRECATION")
        return groupStatesInternal(inGroups, outGroups)
    }

    /** See the documentation for the reflection-based version of [groupStates] */
    inline fun <reified T : ContractState, K : Any> groupStates(selector: (T) -> K): List<InOutGroup<T, K>> {
        val inputs = inStates.filterIsInstance<T>()
        val outputs = outStates.filterIsInstance<T>()

        val inGroups: Map<K, List<T>> = inputs.groupBy(selector)
        val outGroups: Map<K, List<T>> = outputs.groupBy(selector)

        @Suppress("DEPRECATION")
        return groupStatesInternal(inGroups, outGroups)
    }

    @Deprecated("Do not use this directly: exposed as public only due to function inlining")
    fun <T : ContractState, K : Any> groupStatesInternal(inGroups: Map<K, List<T>>, outGroups: Map<K, List<T>>): List<InOutGroup<T, K>> {
        val result = ArrayList<InOutGroup<T, K>>()

        for ((k, v) in inGroups.entries)
            result.add(InOutGroup(v, outGroups[k] ?: emptyList(), k))
        for ((k, v) in outGroups.entries) {
            if (inGroups[k] == null)
                result.add(InOutGroup(emptyList(), v, k))
        }

        return result
    }
}

class TransactionResolutionException(val hash: SecureHash) : Exception()
class TransactionConflictException(val conflictRef: StateRef, val tx1: LedgerTransaction, val tx2: LedgerTransaction) : Exception()

sealed class TransactionVerificationException(val tx: TransactionForVerification, cause: Throwable?) : Exception(cause) {
    class ContractRejection(tx: TransactionForVerification, val contract: Contract, cause: Throwable?) : TransactionVerificationException(tx, cause)
    class MoreThanOneNotary(tx: TransactionForVerification) : TransactionVerificationException(tx, null)
}