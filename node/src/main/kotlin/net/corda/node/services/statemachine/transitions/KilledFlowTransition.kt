package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.FlowException
import net.corda.core.flows.KilledFlowException
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.ErrorSessionMessage
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.FlowError
import net.corda.node.services.statemachine.FlowRemovalReason
import net.corda.node.services.statemachine.InitiatedSessionState
import net.corda.node.services.statemachine.MessageIdentifier
import net.corda.node.services.statemachine.SessionId
import net.corda.node.services.statemachine.SessionState
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

            val (initiatedSessions, newSessions) = bufferErrorMessagesInInitiatingSessions(
                startingState.checkpoint.checkpointState.sessions,
                errorMessages
            )

            val sessionsWithAdvancedSeqNumbers = mutableMapOf<SessionId, SessionState>()
            val errorsPerSession = initiatedSessions
                // no need to send error messages to terminated sessions.
                // after close() changes are merged, these won't be needed since these sessions will have been cleaned up completely.
                .filter { it.initiatedState is InitiatedSessionState.Live }
                .map { sessionState ->
                    val sessionId = (sessionState.initiatedState as InitiatedSessionState.Live).peerSinkSessionId
                    var currentSequenceNumber = sessionState.sequenceNumber
                    val errorsWithId = errorMessages.map { error ->
                        val result = Pair(MessageIdentifier("XX", sessionState.shardId, sessionId.toLong, currentSequenceNumber), error)
                        currentSequenceNumber++
                        result
                    }.toList()
                    val newSessionState = sessionState.copy(sequenceNumber = currentSequenceNumber)
                    sessionsWithAdvancedSeqNumbers[sessionId] = newSessionState
                    Pair(sessionState, errorsWithId)
                }.toMap()

            val newCheckpoint = startingState.checkpoint.setSessions(sessions = newSessions + sessionsWithAdvancedSeqNumbers)
            currentState = currentState.copy(checkpoint = newCheckpoint)

            actions.add(
                Action.PropagateErrors(
                    errorsPerSession,
                    startingState.senderUUID
                )
            )

            if (!startingState.isFlowResumed) {
                actions.add(Action.CreateTransaction)
            }
            // The checkpoint and soft locks are also removed directly in [StateMachineManager.killFlow]
            if (startingState.isAnyCheckpointPersisted) {
                actions.add(Action.RemoveCheckpoint(context.id))
            }
            actions.addAll(
                arrayOf(
                    Action.PersistDeduplicationFacts(currentState.pendingDeduplicationHandlers),
                    Action.ReleaseSoftLocks(context.id.uuid),
                    Action.CommitTransaction,
                    Action.AcknowledgeMessages(currentState.pendingDeduplicationHandlers),
                    Action.RemoveSessionBindings(currentState.checkpoint.checkpointState.sessions.keys)
                )
            )

            currentState = currentState.copy(
                pendingDeduplicationHandlers = emptyList(),
                isRemoved = true
            )

            actions.add(Action.RemoveFlow(context.id, createKilledRemovalReason(killedFlowError), currentState))
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

    // Purposely left the same as [bufferErrorMessagesInInitiatingSessions] in [ErrorFlowTransition] so that it can be refactored
    // Buffer error messages in Initiating sessions, return the initialised ones.
    private fun bufferErrorMessagesInInitiatingSessions(
        sessions: Map<SessionId, SessionState>,
        errorMessages: List<ErrorSessionMessage>
    ): Pair<List<SessionState.Initiated>, Map<SessionId, SessionState>> {
        val newSessions = sessions.mapValues { (sourceSessionId, sessionState) ->
            if (sessionState is SessionState.Initiating && sessionState.rejectionError == null) {
                /**
                 * Error messages are *not* prepended anymore. The other side will process messages in order.
                 */
                var currentSequenceNumber = sessionState.sequenceNumber
                val errorMessagesWithDeduplication = errorMessages.map {
                    val otherSideSessionId = sourceSessionId.toLong + 1
                    (MessageIdentifier("XX", sessionState.shardId, otherSideSessionId, currentSequenceNumber) to it).also { currentSequenceNumber++ }
                }
                sessionState.copy(bufferedMessages =  sessionState.bufferedMessages + errorMessagesWithDeduplication, sequenceNumber = sessionState.sequenceNumber + errorMessages.size)
            } else {
                sessionState
            }
        }
        val initiatedSessions = sessions.values.mapNotNull { session ->
            if (session is SessionState.Initiated && session.errors.isEmpty()) {
                session
            } else {
                null
            }
        }
        return Pair(initiatedSessions, newSessions)
    }

    private fun createKilledRemovalReason(error: FlowError): FlowRemovalReason.ErrorFinish {
        return FlowRemovalReason.ErrorFinish(listOf(error))
    }
}