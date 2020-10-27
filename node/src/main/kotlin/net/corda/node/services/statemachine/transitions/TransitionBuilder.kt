package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.IdentifiableException
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.ErrorState
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.FlowError
import net.corda.node.services.statemachine.SessionId
import net.corda.node.services.statemachine.StateMachineState

// This is a file defining some common utilities for creating state machine transitions.

/**
 * A builder that helps creating [Transition]s. This allows for a more imperative style of specifying the transition.
 */
class TransitionBuilder(val context: TransitionContext, initialState: StateMachineState) {
    /** The current state machine state of the builder */
    var currentState = initialState
    /** The list of actions to execute */
    val actions = ArrayList<Action>()

    /** Check if [currentState] state is errored */
    fun isErrored(): Boolean = currentState.checkpoint.errorState is ErrorState.Errored

    /**
     * Transition the builder into an error state because of a fresh error that happened.
     * Existing actions and the current state are thrown away, and the initial state is dirtied.
     *
     * @param error the error.
     */
    fun freshErrorTransition(error: Throwable, rollback: Boolean = true) {
        val flowError = FlowError(
                errorId = (error as? IdentifiableException)?.errorId ?: context.secureRandom.nextLong(),
                exception = error
        )
        errorTransition(flowError, rollback)
    }

    /**
     * Transition the builder into an error state because of a list of errors that happened.
     * Existing actions and the current state are thrown away, and the initial state is dirtied.
     *
     * @param error the error.
     */
    fun errorsTransition(errors: List<FlowError>, rollback: Boolean) {
        currentState = currentState.copy(
                checkpoint = currentState.checkpoint.copy(
                        errorState = currentState.checkpoint.errorState.addErrors(errors)
                ),
                isFlowResumed = false
        )
        actions.clear()
        if(rollback) {
            actions += Action.RollbackTransaction
        }
        actions += Action.ScheduleEvent(Event.DoRemainingWork)
    }

    /**
     * Transition the builder into an error state because of a non-fresh error has happened.
     * Existing actions and the current state are thrown away, and the initial state is dirtied.
     *
     * @param error the error.
     */
    fun errorTransition(error: FlowError, rollback: Boolean) {
        errorsTransition(listOf(error), rollback)
    }

    fun resumeFlowLogic(result: Any?): FlowContinuation {
        actions.add(Action.CreateTransaction)
        currentState = currentState.copy(isFlowResumed = true, isWaitingForFuture = false, future = null)
        return FlowContinuation.Resume(result)
    }

    fun resumeFlowLogic(result: Throwable): FlowContinuation {
        actions.add(Action.CreateTransaction)
        currentState = currentState.copy(isFlowResumed = true, isWaitingForFuture = false, future = null)
        return FlowContinuation.Throw(result)
    }
}

class CannotFindSessionException(sessionId: SessionId) : IllegalStateException("Couldn't find session with id $sessionId")
class UnexpectedEventInState : IllegalStateException("Unexpected event")
class PrematureSessionCloseException(sessionId: SessionId): IllegalStateException("The following session was closed before it was initialised: $sessionId")
class PrematureSessionEndException(sessionId: SessionId): IllegalStateException("A premature session end message was received before the session was initialised: $sessionId")