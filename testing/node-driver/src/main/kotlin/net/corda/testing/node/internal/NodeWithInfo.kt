package net.corda.testing.node.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.FlowStateMachine
import net.corda.core.node.NodeInfo
import net.corda.node.internal.FlowStarterImpl
import net.corda.node.internal.Node
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.statemachine.ExternalEvent

class NodeWithInfo(val node: Node, val info: NodeInfo) {

    private companion object {
        val UNKNOWN_RPC_USER = "Unknown RPC user"
    }

    val services: StartedNodeServices = object : StartedNodeServices, ServiceHubInternal by node.services, FlowStarter by node.flowStarter {

        override fun <T> startFlow(event: ExternalEvent.ExternalStartFlowEvent<T>): CordaFuture<FlowStateMachine<T>> {
            node.flowMetadataRecorder.record(
                flow = event.flowLogic::class.java,
                invocationContext = event.context,
                startedType = DBCheckpointStorage.StartReason.RPC,
                startedBy = event.context.actor?.id?.value ?: UNKNOWN_RPC_USER
            )
            return node.flowStarter.startFlow(event)
        }

        override fun <T> startFlow(logic: FlowLogic<T>, context: InvocationContext): CordaFuture<FlowStateMachine<T>> {
            node.flowMetadataRecorder.record(
                flow = logic::class.java,
                invocationContext = context,
                startedType = DBCheckpointStorage.StartReason.RPC,
                startedBy = context.actor?.id?.value ?: UNKNOWN_RPC_USER
            )
            return node.flowStarter.startFlow(logic, context)
        }

        override fun <T> invokeFlowAsync(
            logicType: Class<out FlowLogic<T>>,
            context: InvocationContext,
            vararg args: Any?
        ): CordaFuture<FlowStateMachine<T>> {
            node.flowMetadataRecorder.record(
                flow = logicType,
                invocationContext = context,
                startedType = DBCheckpointStorage.StartReason.RPC,
                startedBy = context.actor?.id?.value ?: UNKNOWN_RPC_USER
            )
            return node.flowStarter.invokeFlowAsync(logicType, context)
        }
    }

    fun dispose() = node.stop()
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>) = node.registerInitiatedFlow(node.smm, initiatedFlowClass)
}