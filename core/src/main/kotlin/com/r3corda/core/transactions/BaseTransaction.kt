package com.r3corda.core.transactions

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import java.security.PublicKey
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
         * Keys that are required to have signed the wrapping [SignedTransaction], ordered to match the list of
         * signatures. There is nothing that forces the list to be the _correct_ list of signers for this
         * transaction until the transaction is verified by using [LedgerTransaction.verify]. It includes the
         * notary key, if the notary field is set.
         */
        val mustSign: List<PublicKey>,
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