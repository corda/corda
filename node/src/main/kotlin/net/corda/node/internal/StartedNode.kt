package net.corda.node.internal

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NodeLookup
import net.corda.core.node.StateLoader
import net.corda.core.node.services.TransactionStorage
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.CordaPersistence

interface StartedNode<out N : AbstractNode> {
    val internals: N
    val services: ServiceHubInternal
    val info: NodeInfo
    val checkpointStorage: CheckpointStorage
    val smm: StateMachineManager
    val attachments: NodeAttachmentService
    val inNodeNetworkMapService: NetworkMapService
    val network: MessagingService
    val database: CordaPersistence
    val rpcOps: CordaRPCOps
    val nodeLookup: NodeLookup
    fun dispose() = internals.stop()
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>) = internals.registerInitiatedFlow(initiatedFlowClass)
}

class StateLoaderImpl(private val validatedTransactions: TransactionStorage) : StateLoader {
    @Throws(TransactionResolutionException::class)
    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return stx.resolveBaseTransaction(this).outputs[stateRef.index]
    }
}
