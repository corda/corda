package net.corda.node.internal

import net.corda.core.contracts.*
import net.corda.core.internal.resolveBaseTransaction
import net.corda.core.node.StateLoader
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.TransactionStorage

class StateLoaderImpl(private val validatedTransactions: TransactionStorage, private val attachments: AttachmentStorage) : StateLoader {
    @Throws(TransactionResolutionException::class)
    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = validatedTransactions.getTransaction(stateRef.txhash)
                ?: throw TransactionResolutionException(stateRef.txhash)
        return stx.resolveBaseTransaction(this, attachments).outputs[stateRef.index]
    }

    @Throws(TransactionResolutionException::class)
    // TODO: future implementation to retrieve contract states from a Vault BLOB store
    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return (stateRefs.map { StateAndRef(loadState(it), it) }).toSet()
    }
}