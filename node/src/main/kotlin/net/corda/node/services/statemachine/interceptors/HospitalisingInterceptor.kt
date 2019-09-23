package net.corda.node.services.statemachine.interceptors

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StateMachineRunId
import net.corda.node.services.statemachine.*
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult
import java.util.concurrent.ConcurrentHashMap

/**
 * This interceptor notifies the passed in [flowHospital] in case a flow went through a clean->errored or a errored->clean
 * transition.
 */
class HospitalisingInterceptor(
        private val flowHospital: StaffedFlowHospital,
        private val delegate: TransitionExecutor
) : TransitionExecutor {
    override fun forceRemoveFlow(id: StateMachineRunId) {
        removeFlow(id)
        delegate.forceRemoveFlow(id)
    }

    private fun removeFlow(id: StateMachineRunId) {
        hospitalisedFlows.remove(id)
        flowHospital.flowRemoved(id)
    }

    private val hospitalisedFlows = ConcurrentHashMap<StateMachineRunId, FlowFiber>()

    @Suspendable
    override fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState> {

        // If the fiber's previous state was clean then remove it from the [hospitalisedFlows] map
        // This is important for retrying a flow that has errored during a state machine transition
        if(previousState.checkpoint.errorState is ErrorState.Clean) {
            hospitalisedFlows.remove(fiber.id)
        }

        val (continuation, nextState) = delegate.executeTransition(fiber, previousState, event, transition, actionExecutor)

        if (nextState.checkpoint.errorState is ErrorState.Errored) {
            val exceptionsToHandle = nextState.checkpoint.errorState.errors.map { it.exception }
            if (hospitalisedFlows.putIfAbsent(fiber.id, fiber) == null) {
                flowHospital.flowErrored(fiber, previousState, exceptionsToHandle)
            }
        }
        if (nextState.isRemoved) {
            removeFlow(fiber.id)
        }
        return Pair(continuation, nextState)
    }
}
