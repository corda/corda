package net.corda.core.internal.notary

import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ComponentGroupEnum.OUTPUTS_GROUP
import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

interface TransactionCommitData : NamedByHash {
    val inputs: List<StateRef>
    val references: List<StateRef>
    val timeWindow: TimeWindow
    val outputHashes: List<SecureHash>
    val componentGroupHashes: List<SecureHash>
    val signatures: List<TransactionSignature>

    fun getComponentGroupNonces(type: ComponentGroupEnum): List<SecureHash>

    // TODO This can be updated to support signed filtered txs
    data class Signed(val tx: SignedTransaction) : TransactionCommitData, NamedByHash by tx {
        override val inputs: List<StateRef>
            get() = tx.inputs

        override val references: List<StateRef>
            get() = tx.references

        override val timeWindow: TimeWindow
            get() = requireNotNull(wtx?.timeWindow)

        override val outputHashes: List<SecureHash>
            get() = wtx?.availableComponentHashes?.get(OUTPUTS_GROUP.ordinal) ?: emptyList()

        override val componentGroupHashes: List<SecureHash>
            get() = wtx?.groupHashes ?: emptyList()

        override val signatures: List<TransactionSignature>
            get() = tx.sigs

        override fun getComponentGroupNonces(type: ComponentGroupEnum): List<SecureHash> {
            return wtx?.availableComponentNonces?.get(type.ordinal) ?: emptyList()
        }

        private val wtx: WireTransaction?
            get() = tx.coreTransaction as? WireTransaction

        override fun toString(): String = "TransactionCommitData.Signed($id)"
    }
}
