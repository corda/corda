/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.ConfirmSessionMessage
import net.corda.node.services.statemachine.DataSessionMessage
import net.corda.node.services.statemachine.EndSessionMessage
import net.corda.node.services.statemachine.ErrorSessionMessage
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.ExistingSessionMessage
import net.corda.node.services.statemachine.FlowError
import net.corda.node.services.statemachine.InitiatedSessionState
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
            val existingSession = startingState.checkpoint.sessions[event.sessionMessage.recipientSessionId]
            if (existingSession == null) {
                freshErrorTransition(CannotFindSessionException(event.sessionMessage.recipientSessionId))
            } else {
                val payload = event.sessionMessage.payload
                // Dispatch based on what kind of message it is.
                when (payload) {
                    is ConfirmSessionMessage -> confirmMessageTransition(existingSession, payload)
                    is DataSessionMessage -> dataMessageTransition(existingSession, payload)
                    is ErrorSessionMessage -> errorMessageTransition(existingSession, payload)
                    is RejectSessionMessage -> rejectMessageTransition(existingSession, payload)
                    is EndSessionMessage -> endMessageTransition()
                }
            }
            // Schedule a DoRemainingWork to check whether the flow needs to be woken up.
            actions.add(Action.ScheduleEvent(Event.DoRemainingWork))
            FlowContinuation.ProcessEvents
        }
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
                        initiatedState = InitiatedSessionState.Live(message.initiatedSessionId),
                        errors = emptyList(),
                        deduplicationSeed = sessionState.deduplicationSeed
                )
                val newCheckpoint = currentState.checkpoint.copy(
                        sessions = currentState.checkpoint.sessions + (event.sessionMessage.recipientSessionId to initiatedSession)
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
                        checkpoint = currentState.checkpoint.copy(
                                sessions = startingState.checkpoint.sessions + (event.sessionMessage.recipientSessionId to newSessionState)
                        )
                )
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.errorMessageTransition(sessionState: SessionState, payload: ErrorSessionMessage) {
        val exception: Throwable = if (payload.flowException == null) {
            UnexpectedFlowEndException("Counter-flow errored", cause = null, originalErrorId = payload.errorId)
        } else {
            payload.flowException.originalErrorId = payload.errorId
            payload.flowException
        }

        return when (sessionState) {
            is SessionState.Initiated -> {
                val checkpoint = currentState.checkpoint
                val sessionId = event.sessionMessage.recipientSessionId
                val flowError = FlowError(payload.errorId, exception)
                val newSessionState = sessionState.copy(errors = sessionState.errors + flowError)
                currentState = currentState.copy(
                        checkpoint = checkpoint.copy(
                                sessions = checkpoint.sessions + (sessionId to newSessionState)
                        )
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
                            checkpoint = checkpoint.copy(
                                    sessions = checkpoint.sessions + (sessionId to sessionState.copy(rejectionError = flowError))
                            )
                    )
                }
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.endMessageTransition() {
        val sessionId = event.sessionMessage.recipientSessionId
        val sessions = currentState.checkpoint.sessions
        val sessionState = sessions[sessionId]
        if (sessionState == null) {
            return freshErrorTransition(CannotFindSessionException(sessionId))
        }
        when (sessionState) {
            is SessionState.Initiated -> {
                val newSessionState = sessionState.copy(initiatedState = InitiatedSessionState.Ended)
                currentState = currentState.copy(
                        checkpoint = currentState.checkpoint.copy(
                                sessions = sessions + (sessionId to newSessionState)
                        )
                )
            }
            else -> {
                freshErrorTransition(UnexpectedEventInState())
            }
        }
    }

}
