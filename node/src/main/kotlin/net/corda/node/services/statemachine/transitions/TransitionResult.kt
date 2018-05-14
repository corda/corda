package net.corda.node.services.statemachine.transitions

import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.StateMachineState

/**
 * A datastructure capturing the intended new state of the flow, the actions to be executed as part of the transition
 * and a [FlowContinuation].
 *
 * Read this datastructure as an instruction to the state machine executor:
 *   "Transition to [newState] *if* [actions] execute cleanly. If so, use [continuation] to decide what to do next. If
 *   there was an error it's up to you what to do".
 * Also see [net.corda.node.services.statemachine.TransitionExecutorImpl] on how this is interpreted.
 */
data class TransitionResult(
        val newState: StateMachineState,
        val actions: List<Action> = emptyList(),
        val continuation: FlowContinuation = FlowContinuation.ProcessEvents
)

/**
 * A datastructure describing what to do after a transition has succeeded.
 */
sealed class FlowContinuation {
    /**
     * Return to user code with the supplied [result].
     */
    data class Resume(val result: Any?) : FlowContinuation() {
        override fun toString() = "Resume(result=${result?.javaClass})"
    }

    /**
     * Throw an exception [throwable] in user code.
     */
    data class Throw(val throwable: Throwable) : FlowContinuation()

    /**
     * Keep processing pending events.
     */
    object ProcessEvents : FlowContinuation() {
        override fun toString() = "ProcessEvents"
    }

    /**
     * Immediately abort the flow. Note that this does not imply an error condition.
     */
    object Abort : FlowContinuation() {
        override fun toString() = "Abort"
    }
}
