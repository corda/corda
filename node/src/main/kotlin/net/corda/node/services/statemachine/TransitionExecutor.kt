package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.node.services.statemachine.transitions.FlowContinuation
import net.corda.node.services.statemachine.transitions.TransitionResult

/**
 * An executor of state machine transitions. This is mostly a wrapper interface around an [ActionExecutor], but can be
 * used to create interceptors of transitions.
 */
interface TransitionExecutor {
    @Suspendable
    fun executeTransition(
            fiber: FlowFiber,
            previousState: StateMachineState,
            event: Event,
            transition: TransitionResult,
            actionExecutor: ActionExecutor
    ): Pair<FlowContinuation, StateMachineState>
}

/**
 * An interceptor of a transition. These are currently explicitly hooked up in [SingleThreadedStateMachineManager].
 */
typealias TransitionInterceptor = (TransitionExecutor) -> TransitionExecutor
