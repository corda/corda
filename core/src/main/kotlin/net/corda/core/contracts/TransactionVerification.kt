package net.corda.core.contracts

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.transactions.LedgerTransaction
import java.util.*

// TODO: Consider moving this out of the core module and providing a different way for unit tests to test contracts.

/**
 * A transaction to be passed as input to a contract verification function. Defines helper methods to
 * simplify verification logic in contracts.
 */
data class TransactionForContract(val inputs: List<ContractState>,
                                  val outputs: List<ContractState>,
                                  val attachments: List<Attachment>,
                                  val commands: List<AuthenticatedObject<CommandData>>,
                                  val origHash: SecureHash,
                                  val inputNotary: Party? = null,
                                  val timestamp: Timestamp? = null) {
    override fun hashCode() = origHash.hashCode()
    override fun equals(other: Any?) = other is TransactionForContract && other.origHash == origHash

    /**
     * Given a type and a function that returns a grouping key, associates inputs and outputs together so that they
     * can be processed as one. The grouping key is any arbitrary object that can act as a map key (so must implement
     * equals and hashCode).
     *
     * The purpose of this function is to simplify the writing of verification logic for transactions that may contain
     * similar but unrelated state evolutions which need to be checked independently. Consider a transaction that
     * simultaneously moves both dollars and euros (e.g. is an atomic FX trade). There may be multiple dollar inputs and
     * multiple dollar outputs, depending on things like how fragmented the owner's vault is and whether various privacy
     * techniques are in use. The quantity of dollars on the output side must sum to the same as on the input side, to
     * ensure no money is being lost track of. This summation and checking must be repeated independently for each
     * currency. To solve this, you would use groupStates with a type of Cash.State and a selector that returns the
     * currency field: the resulting list can then be iterated over to perform the per-currency calculation.
     */
    fun <T : ContractState, K : Any> groupStates(ofType: Class<T>, selector: (T) -> K): List<InOutGroup<T, K>> {
        val inputs = inputs.filterIsInstance(ofType)
        val outputs = outputs.filterIsInstance(ofType)

        val inGroups: Map<K, List<T>> = inputs.groupBy(selector)
        val outGroups: Map<K, List<T>> = outputs.groupBy(selector)

        @Suppress("DEPRECATION")
        return groupStatesInternal(inGroups, outGroups)
    }

    /** See the documentation for the reflection-based version of [groupStates] */
    inline fun <reified T : ContractState, K : Any> groupStates(selector: (T) -> K): List<InOutGroup<T, K>> {
        val inputs = inputs.filterIsInstance<T>()
        val outputs = outputs.filterIsInstance<T>()

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

    /** Utilities for contract writers to incorporate into their logic. */

    /**
     * A set of related inputs and outputs that are connected by some common attributes. An InOutGroup is calculated
     * using [groupStates] and is useful for handling cases where a transaction may contain similar but unrelated
     * state evolutions, for example, a transaction that moves cash in two different currencies. The numbers must add
     * up on both sides of the transaction, but the values must be summed independently per currency. Grouping can
     * be used to simplify this logic.
     */
    data class InOutGroup<out T : ContractState, out K : Any>(val inputs: List<T>, val outputs: List<T>, val groupingKey: K)
}

class TransactionResolutionException(val hash: SecureHash) : FlowException() {
    override fun toString(): String = "Transaction resolution failure for $hash"
}

class AttachmentResolutionException(val hash : SecureHash) : FlowException() {
    override fun toString(): String = "Attachment resolution failure for $hash"
}

class TransactionConflictException(val conflictRef: StateRef, val tx1: LedgerTransaction, val tx2: LedgerTransaction) : Exception()

sealed class TransactionVerificationException(val tx: LedgerTransaction, cause: Throwable?) : FlowException(cause) {
    class ContractRejection(tx: LedgerTransaction, val contract: Contract, cause: Throwable?) : TransactionVerificationException(tx, cause)
    class MoreThanOneNotary(tx: LedgerTransaction) : TransactionVerificationException(tx, null)
    class SignersMissing(tx: LedgerTransaction, val missing: List<CompositeKey>) : TransactionVerificationException(tx, null) {
        override fun toString(): String = "Signers missing: ${missing.joinToString()}"
    }
    class DuplicateInputStates(tx: LedgerTransaction, val duplicates: Set<StateRef>) : TransactionVerificationException(tx, null) {
        override fun toString(): String = "Duplicate inputs: ${duplicates.joinToString()}"
    }

    class InvalidNotaryChange(tx: LedgerTransaction) : TransactionVerificationException(tx, null)
    class NotaryChangeInWrongTransactionType(tx: LedgerTransaction, val outputNotary: Party) : TransactionVerificationException(tx, null) {
        override fun toString(): String {
            return "Found unexpected notary change in transaction. Tx notary: ${tx.notary}, found: $outputNotary"
        }
    }

    class TransactionMissingEncumbranceException(tx: LedgerTransaction, val missing: Int, val inOut: Direction) : TransactionVerificationException(tx, null) {
        override val message: String get() = "Missing required encumbrance $missing in $inOut"
    }
    enum class Direction {
        INPUT,
        OUTPUT
    }
}
