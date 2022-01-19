package net.corda.node.internal

import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService
import net.corda.core.internal.FlowStateMachineHandle
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowHandleImpl
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.node.services.ServiceLifecycleObserver
import net.corda.core.node.services.vault.CordaTransactionSupport
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.api.FlowStarter
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleEvent
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleEventsDistributor
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleObserver
import net.corda.nodeapi.internal.lifecycle.NodeLifecycleObserver.Companion.reportSuccess
import rx.Observable
import java.util.*

/**
 * This customizes the ServiceHub for each [net.corda.core.node.services.CordaService] that is initiating flows.
 */
internal class AppServiceHubImpl<T : SerializeAsToken>(private val serviceHub: ServiceHub, private val flowStarter: FlowStarter,
                                                       override val database: CordaTransactionSupport,
                                                       private val nodeLifecycleEventsDistributor: NodeLifecycleEventsDistributor)
        : AppServiceHub, ServiceHub by serviceHub {

    companion object {

        private val logger = contextLogger()

        private class NodeLifecycleServiceObserverAdaptor(private val observer: ServiceLifecycleObserver, override val priority: Int) : NodeLifecycleObserver {
            override fun update(nodeLifecycleEvent: NodeLifecycleEvent): Try<String> {
                return when(nodeLifecycleEvent) {
                    is NodeLifecycleEvent.StateMachineStarted<*> -> Try.on {
                        observer.onServiceLifecycleEvent(ServiceLifecycleEvent.STATE_MACHINE_STARTED)
                        reportSuccess(nodeLifecycleEvent)
                    }
                    else -> super.update(nodeLifecycleEvent)
                }
            }
        }
    }

    lateinit var serviceInstance: T

    @Volatile
    private var flowsAllowed = false

    init {
        nodeLifecycleEventsDistributor.add(object : NodeLifecycleObserver {

            override val priority: Int = AppServiceHub.SERVICE_PRIORITY_HIGH

            override fun update(nodeLifecycleEvent: NodeLifecycleEvent): Try<String> {
                return when(nodeLifecycleEvent) {
                    is NodeLifecycleEvent.StateMachineStarted<*> -> Try.on {
                        flowsAllowed = true
                        reportSuccess(nodeLifecycleEvent)
                    }
                    else -> super.update(nodeLifecycleEvent)
                }
            }
        })
    }

    override fun <T> startTrackedFlow(flow: FlowLogic<T>): FlowProgressHandle<T> {
        val stateMachine = startFlowChecked(flow)
        return FlowProgressHandleImpl(
                id = stateMachine.id,
                returnValue = stateMachine.resultFuture,
                progress = stateMachine.logic?.track()?.updates ?: Observable.empty()
        )
    }

    override fun <T> startFlow(flow: FlowLogic<T>): FlowHandle<T> {
        val parentFlow = FlowLogic.currentTopLevel
        return if (parentFlow != null) {
            val result = parentFlow.subFlow(flow)
            // Accessing the flow id must happen after the flow has started.
            val flowId = flow.runId
            FlowHandleImpl(flowId, doneFuture(result))
        } else {
            val stateMachine = startFlowChecked(flow)
            FlowHandleImpl(id = stateMachine.id, returnValue = stateMachine.resultFuture)
        }
    }

    private fun <T> startFlowChecked(flow: FlowLogic<T>): FlowStateMachineHandle<T> {
        val logicType = flow.javaClass
        require(logicType.isAnnotationPresent(StartableByService::class.java)) { "${logicType.name} was not designed for starting by a CordaService" }
        // TODO check service permissions
        // TODO switch from myInfo.legalIdentities[0].name to current node's identity as soon as available
        if(!flowsAllowed) {
            logger.warn("Flow $flow started too early in the node's lifecycle, SMM may not be ready yet. " +
                    "Please consider registering your service to node's lifecycle event: `STATE_MACHINE_STARTED`")
        }
        val context = InvocationContext.service(serviceInstance.javaClass.name, myInfo.legalIdentities[0].name)
        return flowStarter.startFlow(flow, context).getOrThrow()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppServiceHubImpl<*>) return false
        return serviceHub == other.serviceHub
                && flowStarter == other.flowStarter
                && serviceInstance == other.serviceInstance
    }

    override fun hashCode() = Objects.hash(serviceHub, flowStarter, serviceInstance)

    override fun register(priority: Int, observer: ServiceLifecycleObserver) {
        nodeLifecycleEventsDistributor.add(NodeLifecycleServiceObserverAdaptor(observer, priority))
    }
}