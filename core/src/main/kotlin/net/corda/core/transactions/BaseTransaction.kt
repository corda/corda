package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import java.util.*

/**
 * An abstract class defining fields shared by all transaction types in the system.
 */
abstract class BaseTransaction(
        /** The inputs of this transaction. Note that in BaseTransaction subclasses the type of this list may change! */
        open val inputs: List<*>,
        /** Ordered list of states defined by this transaction, along with the associated notaries. */
        val outputs: List<TransactionState<ContractState>>,
        /**
         * If present, the notary for this transaction. If absent then the transaction is not notarised at all.
         * This is intended for issuance/genesis transactions that don't consume any other states and thus can't
         * double spend anything.
         */
        val notary: Party?,
        /**
         * Composite keys that need to be fulfilled by signatures in order for the transaction to be valid.
         * In a [SignedTransaction] this list is used to check whether there are any missing signatures. Note that
         * there is nothing that forces the list to be the _correct_ list of signers for this transaction until
         * the transaction is verified by using [LedgerTransaction.verify].
         *
         * It includes the notary key, if the notary field is set.
         */
        val mustSign: List<CompositeKey>,
        /**
         * Pointer to a class that defines the behaviour of this transaction: either normal, or "notary changing".
         */
        val type: TransactionType,
        /**
         * If specified, a time window in which this transaction may have been notarised. Contracts can check this
         * time window to find out when a transaction is deemed to have occurred, from the ledger's perspective.
         */
        val timestamp: Timestamp?
) : NamedByHash {

    protected fun checkInvariants() {
        if (notary == null) check(inputs.isEmpty()) { "The notary must be specified explicitly for any transaction that has inputs." }
        if (timestamp != null) check(notary != null) { "If a timestamp is provided, there must be a notary." }
    }

    override fun equals(other: Any?) =
            other is BaseTransaction &&
            notary == other.notary &&
            mustSign == other.mustSign &&
            type == other.type &&
            timestamp == other.timestamp

    override fun hashCode() = Objects.hash(notary, mustSign, type, timestamp)
}
