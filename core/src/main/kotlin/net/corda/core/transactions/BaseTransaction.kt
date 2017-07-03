package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.identity.Party
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
         * Pointer to a class that defines the behaviour of this transaction: either normal, or "notary changing".
         *
         * TODO: this field can be removed – transaction type check can be done based on existence of the NotaryChange command
         */
        val type: TransactionType,
        /**
         * If specified, a time window in which this transaction may have been notarised. Contracts can check this
         * time window to find out when a transaction is deemed to have occurred, from the ledger's perspective.
         */
        val timeWindow: TimeWindow?
) : NamedByHash {

    protected fun checkInvariants() {
        if (notary == null) check(inputs.isEmpty()) { "The notary must be specified explicitly for any transaction that has inputs" }
        if (timeWindow != null) check(notary != null) { "If a time-window is provided, there must be a notary" }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        return other is BaseTransaction &&
                notary == other.notary &&
                type == other.type &&
                timeWindow == other.timeWindow
    }

    override fun hashCode() = Objects.hash(notary, type, timeWindow)

    override fun toString(): String = "${javaClass.simpleName}(id=$id)"
}
