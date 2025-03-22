package net.corda.core.internal.notary

import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

/**
 * The minimum amount of information needed to notarise a transaction. Note that this does not include
 * any sensitive transaction details.
 */
interface TransactionParts : NamedByHash {
    val componentGroupHashes: List<SecureHash>
    val inputs: List<StateRef>
    val timeWindow: TimeWindow?
    val notary: Party?
    val references: List<StateRef>
    val networkParametersHash: SecureHash?

    data class Signed(val tx: SignedTransaction) : TransactionParts, NamedByHash by tx {
        override val componentGroupHashes: List<SecureHash>
            get() = (tx.coreTransaction as? WireTransaction)?.groupHashes ?: emptyList()
        override val inputs: List<StateRef>
            get() = tx.inputs
        override val timeWindow: TimeWindow?
            get() = (tx.coreTransaction as? WireTransaction)?.timeWindow
        override val notary: Party?
            get() = tx.notary
        override val references: List<StateRef>
            get() = tx.references
        override val networkParametersHash: SecureHash?
            get() = tx.networkParametersHash
    }

    data class Filtered(val tx: FilteredTransaction) : TransactionParts, NamedByHash by tx {
        override val componentGroupHashes: List<SecureHash>
            get() = tx.groupHashes
        override val inputs: List<StateRef>
            get() = tx.inputs
        override val timeWindow: TimeWindow?
            get() = tx.timeWindow
        override val notary: Party?
            get() = tx.notary
        override val references: List<StateRef>
            get() = tx.references
        override val networkParametersHash: SecureHash?
            get() = tx.networkParametersHash
    }

    data class Core(val tx: CoreTransaction) : TransactionParts, NamedByHash by tx {
        override val componentGroupHashes: List<SecureHash>
            get() = emptyList()
        override val inputs: List<StateRef>
            get() = tx.inputs
        override val timeWindow: TimeWindow?
            get() = null
        override val notary: Party?
            get() = tx.notary
        override val references: List<StateRef>
            get() = tx.references
        override val networkParametersHash: SecureHash?
            get() = tx.networkParametersHash
    }
}
