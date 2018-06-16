package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StateMachineRunId
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

    /**
     * Called if the normal exit path where the new state is marked as removed via [StateMachineState.isRemoved] is not called.
     * Currently this only happens via [StateMachineManager.killFlow].  This allows instances of this interface to clean up
     * any state they are holding for a flow to prevent a memory leak.
     */
    fun forceRemoveFlow(id: StateMachineRunId)
}

/**
 * An interceptor of a transition. These are currently explicitly hooked up in [SingleThreadedStateMachineManager].
 */
typealias TransitionInterceptor = (TransitionExecutor) -> TransitionExecutor
