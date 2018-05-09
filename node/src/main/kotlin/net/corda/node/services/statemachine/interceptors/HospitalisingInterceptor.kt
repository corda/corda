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
        private val flowHospital: FlowHospital,
        private val delegate: TransitionExecutor
) : TransitionExecutor {
    private val hospitalisedFlows = ConcurrentHashMap<StateMachineRunId, FlowFiber>()

    @Suspendable
    override fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState> {
        val skipError = if (event is Event.Error && hospitalisedFlows.putIfAbsent(fiber.id, fiber) == null) {
            !flowHospital.flowErrored(fiber, previousState, event.exception)
        } else {
            false
        }
        val (continuation, nextState) = if (!skipError) {
            delegate.executeTransition(fiber, previousState, event, transition, actionExecutor)
        } else {
            val doNotErrorTransition = TransitionResult(previousState, listOf(Action.RollbackTransaction), FlowContinuation.ProcessEvents)
            delegate.executeTransition(fiber, previousState, event, doNotErrorTransition, actionExecutor)
        }
        if (!skipError) {
            when (nextState.checkpoint.errorState) {
                is ErrorState.Clean -> {
                    if (hospitalisedFlows.remove(fiber.id) != null) {
                        flowHospital.flowCleaned(fiber)
                    }
                }
                is ErrorState.Errored -> {
                    val exceptionToHandle = nextState.checkpoint.errorState.errors.last().exception
                    if (hospitalisedFlows.putIfAbsent(fiber.id, fiber) == null && !flowHospital.flowErrored(fiber, previousState, exceptionToHandle)) {
                        val revisedState = nextState.copy(checkpoint = nextState.checkpoint.copy(errorState = ErrorState.Clean))
                        return Pair(continuation, revisedState)
                    }
                }
            }
        }
        if (nextState.isRemoved) {
            hospitalisedFlows.remove(fiber.id)
            flowHospital.flowRemoved(fiber)
        }
        return Pair(continuation, nextState)
    }
}
