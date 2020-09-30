package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.FlowException
import net.corda.core.flows.KilledFlowException
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.ErrorSessionMessage
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.FlowError
import net.corda.node.services.statemachine.FlowRemovalReason
import net.corda.node.services.statemachine.FlowState
import net.corda.node.services.statemachine.StateMachineState

class KilledFlowTransition(
    override val context: TransitionContext,
    override val startingState: StateMachineState,
    val event: Event
) : Transition {

    override fun transition(): TransitionResult {
        return builder {

            val killedFlowError = createKilledFlowError()
            val killedFlowErrorMessage = createErrorMessageFromError(killedFlowError)
            val errorMessages = listOf(killedFlowErrorMessage)

            val (propagateErrorsAction, newSessionStates) = ErrorFlowTransition.sendAndBufferErrorMessages(startingState, errorMessages)

            val newCheckpoint = startingState.checkpoint.copy(
                    status = Checkpoint.FlowStatus.KILLED,
                    flowState = FlowState.Finished,
                    checkpointState = startingState.checkpoint.checkpointState.copy(sessions = newSessionStates)
            )

            currentState = currentState.copy(
                    checkpoint = newCheckpoint,
                    pendingDeduplicationHandlers = emptyList(),
                    closedSessionsPendingToBeSignalled = emptyMap(),
                    isRemoved = true
            )

            actions += propagateErrorsAction

            if (!startingState.isFlowResumed) {
                actions += Action.CreateTransaction
            }

            // The checkpoint is updated/removed and soft locks are removed directly in [StateMachineManager.killFlow] as well
            if (currentState.checkpoint.checkpointState.invocationContext.clientId == null) {
                actions += Action.RemoveCheckpoint(context.id, mayHavePersistentResults = true)
            } else if (startingState.isAnyCheckpointPersisted) {
                actions += Action.UpdateFlowStatus(context.id, Checkpoint.FlowStatus.KILLED)
                actions += Action.RemoveFlowException(context.id)
                actions += Action.AddFlowException(context.id, killedFlowError.exception)
            }

            val signalSessionsEndMap = currentState.checkpoint.checkpointState.sessions.map { (sessionId, sessionState) ->
                sessionId to Pair(sessionState.lastSenderUUID, sessionState.lastSenderSeqNo)
            }.toMap()

            actions += Action.PersistDeduplicationFacts(startingState.pendingDeduplicationHandlers)
            actions += Action.SignalSessionsHasEnded(signalSessionsEndMap)
            actions += Action.ReleaseSoftLocks(context.id.uuid)
            actions += Action.CommitTransaction(currentState)
            actions += Action.AcknowledgeMessages(startingState.pendingDeduplicationHandlers)
            actions += Action.RemoveSessionBindings(startingState.checkpoint.checkpointState.sessions.keys)
            actions += Action.RemoveFlow(context.id, createKilledRemovalReason(killedFlowError), currentState)

            FlowContinuation.Abort
        }
    }

    private fun createKilledFlowError(): FlowError {
        val exception = when (event) {
            is Event.Error -> event.exception
            else -> KilledFlowException(context.id)
        }
        return FlowError(context.secureRandom.nextLong(), exception)
    }

    // Purposely left the same as [bufferErrorMessagesInInitiatingSessions] in [ErrorFlowTransition] so that it can be refactored
    private fun createErrorMessageFromError(error: FlowError): ErrorSessionMessage {
        val exception = error.exception
        // If the exception doesn't contain an originalErrorId that means it's a fresh FlowException that should
        // propagate to the neighbouring flows. If it has the ID filled in that means it's a rethrown FlowException and
        // shouldn't be propagated.
        return if (exception is FlowException && exception.originalErrorId == null) {
            ErrorSessionMessage(flowException = exception, errorId = error.errorId)
        } else {
            ErrorSessionMessage(flowException = null, errorId = error.errorId)
        }
    }

    private fun createKilledRemovalReason(error: FlowError): FlowRemovalReason.ErrorFinish {
        return FlowRemovalReason.ErrorFinish(listOf(error))
    }
}