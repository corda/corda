package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.ConfirmSessionMessage
import net.corda.node.services.statemachine.DataSessionMessage
import net.corda.node.services.statemachine.EndSessionMessage
import net.corda.node.services.statemachine.ErrorSessionMessage
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.ExistingSessionMessage
import net.corda.node.services.statemachine.FlowError
import net.corda.node.services.statemachine.FlowState
import net.corda.node.services.statemachine.RejectSessionMessage
import net.corda.node.services.statemachine.SenderDeduplicationId
import net.corda.node.services.statemachine.SessionState
import net.corda.node.services.statemachine.StateMachineState

/**
 * This transition handles incoming session messages. It handles the following cases:
 *  - DataSessionMessage: these arrive to initiated and confirmed sessions and are expected to be received by the flow.
 *  - ConfirmSessionMessage: these arrive as a response to an InitialSessionMessage and include information about the
 *      counterparty flow's session ID as well as their [FlowInfo].
 *  - ErrorSessionMessage: these arrive to initiated and confirmed sessions and put the corresponding session into an
 *      "errored" state. This means that whenever that session is subsequently interacted with the error will be thrown
 *      in the flow.
 *  - RejectSessionMessage: these arrive as a response to an InitialSessionMessage when the initiation failed. It
 *      behaves similarly to ErrorSessionMessage aside from the type of exceptions stored/raised.
 *  - EndSessionMessage: these are sent when the counterparty flow has finished. They put the corresponding session into
 *      an "ended" state. This means that subsequent sends on this session will fail, and receives will start failing
 *      after the buffer of already received messages is drained.
 */
class DeliverSessionMessageTransition(
        override val context: TransitionContext,
        override val startingState: StateMachineState,
        val event: Event.DeliverSessionMessage
) : Transition {

    private companion object {
        val log = contextLogger()
    }

    override fun transition(): TransitionResult {
        return builder {
            // Add the DeduplicationHandler to the pending ones ASAP so in case an error happens we still know
            // about the message. Note that in case of an error during deliver this message *will be acked*.
            // For example if the session corresponding to the message is not found the message is still acked to free
            // up the broker but the flow will error.
            currentState = currentState.copy(
                    pendingDeduplicationHandlers = currentState.pendingDeduplicationHandlers + event.deduplicationHandler
            )
            // Check whether we have a session corresponding to the message.
            val existingSession = startingState.checkpoint.checkpointState.sessions[event.sessionMessage.recipientSessionId]
            if (existingSession == null) {
                checkIfMissingSessionIsAnIssue(event.sessionMessage)
            } else {
                val payload = event.sessionMessage.payload
                // Dispatch based on what kind of message it is.
                when (payload) {
                    is ConfirmSessionMessage -> confirmMessageTransition(existingSession, payload)
                    is DataSessionMessage -> dataMessageTransition(existingSession, payload)
                    is ErrorSessionMessage -> errorMessageTransition(existingSession, payload)
                    is RejectSessionMessage -> rejectMessageTransition(existingSession, payload)
                    is EndSessionMessage -> endMessageTransition(payload)
                }
            }
            // Schedule a DoRemainingWork to check whether the flow needs to be woken up.
            actions.add(Action.ScheduleEvent(Event.DoRemainingWork))
            FlowContinuation.ProcessEvents
        }
    }

    private fun TransitionBuilder.checkIfMissingSessionIsAnIssue(message: ExistingSessionMessage) {
        val payload = message.payload
        if (payload is EndSessionMessage)
            log.debug { "Received session end message for a session that has already ended: ${event.sessionMessage.recipientSessionId}"}
        else
            freshErrorTransition(CannotFindSessionException(event.sessionMessage.recipientSessionId))
    }

    private fun TransitionBuilder.confirmMessageTransition(sessionState: SessionState, message: ConfirmSessionMessage) {
        // We received a confirmation message. The corresponding session state must be Initiating.
        when (sessionState) {
            is SessionState.Initiating -> {
                // Create the new session state that is now Initiated.
                val initiatedSession = SessionState.Initiated(
                        peerParty = event.sender,
                        peerFlowInfo = message.initiatedFlowInfo,
                        receivedMessages = emptyList(),
                        peerSinkSessionId = message.initiatedSessionId,
                        deduplicationSeed = sessionState.deduplicationSeed,
                        otherSideErrored = false
                )
                val newCheckpoint = currentState.checkpoint.addSession(
                        event.sessionMessage.recipientSessionId to initiatedSession
                )
                // Send messages that were buffered pending confirmation of session.
                val sendActions = sessionState.bufferedMessages.map { (deduplicationId, bufferedMessage) ->
                    val existingMessage = ExistingSessionMessage(message.initiatedSessionId, bufferedMessage)
                    Action.SendExisting(initiatedSession.peerParty, existingMessage, SenderDeduplicationId(deduplicationId, startingState.senderUUID))
                }
                actions.addAll(sendActions)
                currentState = currentState.copy(checkpoint = newCheckpoint)
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.dataMessageTransition(sessionState: SessionState, message: DataSessionMessage) {
        // We received a data message. The corresponding session must be Initiated.
        return when (sessionState) {
            is SessionState.Initiated -> {
                // Buffer the message in the session's receivedMessages buffer.
                val newSessionState = sessionState.copy(
                        receivedMessages = sessionState.receivedMessages + message
                )

                currentState = currentState.copy(
                        checkpoint = currentState.checkpoint.addSession(
                                event.sessionMessage.recipientSessionId to newSessionState
                        )
                )
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.errorMessageTransition(sessionState: SessionState, payload: ErrorSessionMessage) {
        return when (sessionState) {
            is SessionState.Initiated -> {
                val checkpoint = currentState.checkpoint
                val sessionId = event.sessionMessage.recipientSessionId
                val newSessionState = sessionState.copy(receivedMessages = sessionState.receivedMessages + payload)
                currentState = currentState.copy(
                        checkpoint = checkpoint.addSession(sessionId to newSessionState)
                )
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.rejectMessageTransition(sessionState: SessionState, payload: RejectSessionMessage) {
        val exception = UnexpectedFlowEndException(payload.message, cause = null, originalErrorId = payload.errorId)
        return when (sessionState) {
            is SessionState.Initiating -> {
                if (sessionState.rejectionError != null) {
                    // Double reject
                    freshErrorTransition(UnexpectedEventInState())
                } else {
                    val checkpoint = currentState.checkpoint
                    val sessionId = event.sessionMessage.recipientSessionId
                    val flowError = FlowError(payload.errorId, exception)
                    currentState = currentState.copy(
                            checkpoint = checkpoint.addSession(sessionId to sessionState.copy(rejectionError = flowError))
                    )
                }
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.endMessageTransition(payload: EndSessionMessage) {

        val sessionId = event.sessionMessage.recipientSessionId
        val sessions = currentState.checkpoint.checkpointState.sessions
        // a check has already been performed to confirm the session exists for this message before this method is invoked.
        val sessionState = sessions[sessionId]!!
        when (sessionState) {
            is SessionState.Initiated -> {
                val flowState = currentState.checkpoint.flowState
                // flow must have already been started when session end messages are being delivered.
                if (flowState !is FlowState.Started)
                    return freshErrorTransition(UnexpectedEventInState())

                val newSessionState = sessionState.copy(receivedMessages = sessionState.receivedMessages + payload)
                val newCheckpoint = currentState.checkpoint.addSession(event.sessionMessage.recipientSessionId to newSessionState)
                                                           .addSessionsToBeClosed(setOf(event.sessionMessage.recipientSessionId))
                currentState = currentState.copy(checkpoint = newCheckpoint)
            }
            else -> {
                freshErrorTransition(PrematureSessionEndException(event.sessionMessage.recipientSessionId))
            }
        }
    }

}
