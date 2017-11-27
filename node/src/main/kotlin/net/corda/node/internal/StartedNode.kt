package net.corda.node.internal

import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.internal.VisibleForTesting
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.StateLoader
import net.corda.core.node.services.NotaryService
import net.corda.core.node.services.TransactionStorage
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.CordaPersistence
import rx.Observable

interface StartedNode<out N : AbstractNode> {
    val internals: N
    val services: StartedNodeServices
    val info: NodeInfo
    val checkpointStorage: CheckpointStorage
    val smm: StateMachineManager
    val attachments: NodeAttachmentService
    val network: MessagingService
    val database: CordaPersistence
    val rpcOps: CordaRPCOps
    val notaryService: NotaryService?
    fun dispose() = internals.stop()
    /**
     * Use this method to register your initiated flows in your tests. This is automatically done by the node when it
     * starts up for all [FlowLogic] classes it finds which are annotated with [InitiatedBy].
     * @return An [Observable] of the initiated flows started by counter-parties.
     */
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>) = internals.registerInitiatedFlow(smm, initiatedFlowClass)

    @VisibleForTesting
    fun <F : FlowLogic<*>> internalRegisterFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>,
                                                       flowFactory: InitiatedFlowFactory<F>,
                                                       initiatedFlowClass: Class<F>,
                                                       track: Boolean): Observable<F> {
        return internals.internalRegisterFlowFactory(smm, initiatingFlowClass, flowFactory, initiatedFlowClass, track)
    }
}

class StateLoaderImpl(private val validatedTransactions: TransactionStorage) : StateLoader {
    @Throws(TransactionResolutionException::class)
    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return stx.resolveBaseTransaction(this).outputs[stateRef.index]
    }

    @Throws(TransactionResolutionException::class)
    // TODO: future implementation to retrieve contract states from a Vault BLOB store
    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return (stateRefs.map { StateAndRef(loadState(it), it) }).toSet()
    }
}
