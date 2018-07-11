package net.corda.node.services.statemachine.interceptors

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StateMachineRunId
import net.corda.core.utilities.contextLogger
import net.corda.node.services.statemachine.ActionExecutor
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.FlowFiber
import net.corda.node.services.statemachine.StateMachineState
import net.corda.node.services.statemachine.TransitionExecutor
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult
import java.time.Instant

/**
 * This interceptor simply prints all state machine transitions. Useful for debugging.
 */
class PrintingInterceptor(val delegate: TransitionExecutor) : TransitionExecutor {
    override fun forceRemoveFlow(id: StateMachineRunId) {
        delegate.forceRemoveFlow(id)
    }

    companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState> {
        val (continuation, nextState) = delegate.executeTransition(fiber, previousState, event, transition, actionExecutor)
        val transitionRecord = TransitionDiagnosticRecord(Instant.now(), fiber.id, previousState, nextState, event, transition, continuation)
        log.info("Transition for flow ${fiber.id} $transitionRecord")
        return Pair(continuation, nextState)
    }
}
