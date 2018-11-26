package net.corda.node.internal

import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.NetworkParametersStorage
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.TransactionStorage
import net.corda.core.transactions.TraversableTransaction

data class ServicesForResolutionImpl(
        override val identityService: IdentityService,
        override val attachments: AttachmentStorage,
        override val cordappProvider: CordappProvider,
        override val networkParametersStorage: NetworkParametersStorage,
        private val validatedTransactions: TransactionStorage
) : ServicesForResolution {
    override val networkParameters: NetworkParameters get() = networkParametersStorage.lookup(networkParametersStorage.currentHash) ?:
            throw IllegalArgumentException("No current parameters in network parameters storage")

    @Throws(TransactionResolutionException::class)
    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return stx.resolveBaseTransaction(this).outputs[stateRef.index]
    }

    @Throws(TransactionResolutionException::class)
    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return stateRefs.groupBy { it.txhash }.flatMap {
            val stx = validatedTransactions.getTransaction(it.key) ?: throw TransactionResolutionException(it.key)
            val baseTx = stx.resolveBaseTransaction(this)
            it.value.map { StateAndRef(baseTx.outputs[it.index], it) }
        }.toSet()
    }

    override fun loadContractAttachment(stateRef: StateRef): Attachment? {
        val stx = validatedTransactions.getTransaction(stateRef.txhash)
        if (stx != null) {
            val transactionState = stx.coreTransaction.outRef<ContractState>(stateRef.index).state
            if (stx.coreTransaction is TraversableTransaction) {
                for (attachmentId in (stx.coreTransaction as TraversableTransaction).attachments) {
                    val attachment = attachments.openAttachment(attachmentId)
                    if (attachment is ContractAttachment && transactionState.contract == attachment.contract) {
                        return attachment
                    }
                }
            } //TODO branches for upgrade transaction and  the notary change transaction, see WireTransaction.resolveStateRefBinaryComponent as an example
        }
        return null
    }
}
