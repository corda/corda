package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.FlowException
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.DeclaredField
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.ConfirmSessionMessage
import net.corda.node.services.statemachine.DataSessionMessage
import net.corda.node.services.statemachine.EndSessionMessage
import net.corda.node.services.statemachine.ErrorSessionMessage
import net.corda.node.services.statemachine.Event
import net.corda.node.services.statemachine.ExistingSessionMessage
import net.corda.node.services.statemachine.FlowError
import net.corda.node.services.statemachine.InitiatedSessionState
import net.corda.node.services.statemachine.MessageIdentifier
import net.corda.node.services.statemachine.RejectSessionMessage
import net.corda.node.services.statemachine.SenderDeduplicationId
import net.corda.node.services.statemachine.SenderDeduplicationInfo
import net.corda.node.services.statemachine.SessionState
import net.corda.node.services.statemachine.StateMachineState
import java.util.*

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

    companion object {
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
                freshErrorTransition(CannotFindSessionException(event.sessionMessage.recipientSessionId))
            } else {
                val payload = event.sessionMessage.payload
                // Dispatch based on what kind of message it is.
                when (payload) {
                    is ConfirmSessionMessage -> confirmMessageTransition(existingSession, payload, event.senderDeduplicationInfo)
                    is DataSessionMessage -> dataMessageTransition(existingSession, payload, event.messageIdentifier, event.senderDeduplicationInfo)
                    is ErrorSessionMessage -> errorMessageTransition(existingSession, payload, event.messageIdentifier, event.senderDeduplicationInfo)
                    is RejectSessionMessage -> rejectMessageTransition(existingSession, payload, event.senderDeduplicationInfo)
                    is EndSessionMessage -> endMessageTransition(event.messageIdentifier, event.senderDeduplicationInfo)
                }
            }
            // Schedule a DoRemainingWork to check whether the flow needs to be woken up.
            actions.add(Action.ScheduleEvent(Event.DoRemainingWork))
            FlowContinuation.ProcessEvents
        }
    }

    private fun TransitionBuilder.confirmMessageTransition(sessionState: SessionState, message: ConfirmSessionMessage, senderDeduplicationInfo: SenderDeduplicationInfo) {
        // We received a confirmation message. The corresponding session state must be Initiating.
        when (sessionState) {
            is SessionState.Initiating -> {
                // Create the new session state that is now Initiated.
                val initiatedSession = SessionState.Initiated(
                        peerParty = event.sender,
                        peerFlowInfo = message.initiatedFlowInfo,
                        receivedMessages = TreeMap(),
                        initiatedState = InitiatedSessionState.Live(message.initiatedSessionId),
                        errors = TreeMap(),
                        toBeTerminated = null,
                        deduplicationSeed = sessionState.deduplicationSeed,
                        sequenceNumber = sessionState.sequenceNumber,
                        lastSequenceNumberProcessed = 0,
                        shardId = sessionState.shardId,
                        lastDedupInfo = senderDeduplicationInfo
                )
                val newCheckpoint = currentState.checkpoint.addSession(
                        event.sessionMessage.recipientSessionId to initiatedSession
                )
                // Send messages that were buffered pending confirmation of session.
                val sendActions = sessionState.bufferedMessages.map { (messageId, bufferedMessage) ->
                    val existingMessage = ExistingSessionMessage(message.initiatedSessionId, bufferedMessage)
                    Action.SendExisting(initiatedSession.peerParty, existingMessage, SenderDeduplicationId(messageId, startingState.senderUUID))
                }
                actions.addAll(sendActions)
                currentState = currentState.copy(checkpoint = newCheckpoint)
            }
            is SessionState.Initiated -> {
                log.trace { "Discarding duplicate confirmation for session ${event.sessionMessage.recipientSessionId} with ${sessionState.peerParty}" }
            }
            else -> {
                log.info("Received a confirmation while at $sessionState")
                freshErrorTransition(UnexpectedEventInState())
            }
        }
    }

    private fun isLatestSeqNo(seqNo: Int, sessionState: SessionState.Initiated): Boolean {
        return (sessionState.receivedMessages.isEmpty() || seqNo >= sessionState.receivedMessages.lastKey()) &&
                (sessionState.errors.isEmpty() || seqNo >= sessionState.errors.lastKey()) &&
                (sessionState.toBeTerminated == null || seqNo >= sessionState.toBeTerminated.first)
    }

    private fun TransitionBuilder.dataMessageTransition(sessionState: SessionState, message: DataSessionMessage, messageIdentifier: MessageIdentifier, senderDeduplicationInfo: SenderDeduplicationInfo) {
        val sequenceNumber = messageIdentifier.sessionSequenceNumber
        // We received a data message. Due to re-ordering, the session might not have been initiated yet, so buffering the message.
        return when (sessionState) {
            is SessionState.Initiated -> {
                if (sequenceNumber > sessionState.lastSequenceNumberProcessed) {
                    // Buffer the message in the session's receivedMessages buffer.
                    sessionState.receivedMessages[sequenceNumber] = Pair(message, senderDeduplicationInfo)
                    val lastDedupInfo = if (isLatestSeqNo(sequenceNumber, sessionState)) {
                        senderDeduplicationInfo
                    } else {
                        sessionState.lastDedupInfo
                    }
                    val newSessionState = sessionState.copy(
                            receivedMessages = sessionState.receivedMessages,
                            lastDedupInfo = lastDedupInfo
                    )

                    currentState = currentState.copy(
                            checkpoint = currentState.checkpoint.addSession(
                                    event.sessionMessage.recipientSessionId to newSessionState
                            )
                    )
                } else {
                    log.trace { "Discarding data message for session ${event.sessionMessage.recipientSessionId} with ${sessionState.peerParty}" }
                }
            }
            is SessionState.Initiating -> {
                // Buffer the message in the session's receivedMessages buffer.
                sessionState.receivedMessages[sequenceNumber] = Pair(message, senderDeduplicationInfo)
                val newSessionState = sessionState.copy(
                        receivedMessages = sessionState.receivedMessages
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

    private fun TransitionBuilder.errorMessageTransition(sessionState: SessionState, payload: ErrorSessionMessage, messageIdentifier: MessageIdentifier, senderDeduplicationInfo: SenderDeduplicationInfo) {
        val sequenceNumber = messageIdentifier.sessionSequenceNumber
        val exception: Throwable = if (payload.flowException == null) {
            UnexpectedFlowEndException("Counter-flow errored", cause = null, originalErrorId = payload.errorId)
        } else {
            payload.flowException.originalErrorId = payload.errorId
            payload.flowException
        }

        return when (sessionState) {
            is SessionState.Initiated -> {
                if (sequenceNumber > sessionState.lastSequenceNumberProcessed) {
                    when (exception) {
                        // reflection used to access private field
                        is UnexpectedFlowEndException -> DeclaredField<Party?>(
                                UnexpectedFlowEndException::class.java,
                                "peer",
                                exception
                        ).value = sessionState.peerParty
                        is FlowException -> DeclaredField<Party?>(FlowException::class.java, "peer", exception).value = sessionState.peerParty
                    }
                    val checkpoint = currentState.checkpoint
                    val sessionId = event.sessionMessage.recipientSessionId
                    val flowError = FlowError(payload.errorId, exception)
                    sessionState.errors[sequenceNumber] = Pair(flowError, senderDeduplicationInfo)
                    val lastDedupInfo = if (isLatestSeqNo(sequenceNumber, sessionState)) {
                        senderDeduplicationInfo
                    } else {
                        sessionState.lastDedupInfo
                    }
                    val newSessionState = sessionState.copy(lastDedupInfo = lastDedupInfo)
                    currentState = currentState.copy(
                            checkpoint = checkpoint.addSession(sessionId to newSessionState)
                    )
                } else {
                    log.trace { "Discarding error message for session ${event.sessionMessage.recipientSessionId} with ${sessionState.peerParty}" }
                }
            }
            is SessionState.Initiating -> {
                when (exception) {
                    // reflection used to access private field
                    is UnexpectedFlowEndException -> DeclaredField<Party?>(
                            UnexpectedFlowEndException::class.java,
                            "peer",
                            exception
                    ).value = sessionState.peerParty
                    is FlowException -> DeclaredField<Party?>(FlowException::class.java, "peer", exception).value = sessionState.peerParty
                }
                val checkpoint = currentState.checkpoint
                val sessionId = event.sessionMessage.recipientSessionId
                val flowError = FlowError(payload.errorId, exception)
                sessionState.errors[sequenceNumber] = Pair(flowError, senderDeduplicationInfo)
                currentState = currentState.copy(
                        checkpoint = checkpoint.addSession(sessionId to sessionState)
                )
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.rejectMessageTransition(sessionState: SessionState, payload: RejectSessionMessage, senderDeduplicationInfo: SenderDeduplicationInfo) {
        val exception = UnexpectedFlowEndException(payload.message, cause = null, originalErrorId = payload.errorId)
        return when (sessionState) {
            is SessionState.Initiating -> {
                if (sessionState.receivedMessages.isNotEmpty() || sessionState.errors.isNotEmpty()) {
                    // cannot have received messages from a session that has failed to initialise.
                    freshErrorTransition(UnexpectedEventInState())
                } else {
                    if (sessionState.rejectionError != null) {
                        // Double reject
                        freshErrorTransition(UnexpectedEventInState())
                    } else {
                        val checkpoint = currentState.checkpoint
                        val sessionId = event.sessionMessage.recipientSessionId
                        val flowError = FlowError(payload.errorId, exception)
                        currentState = currentState.copy(
                                checkpoint = checkpoint.addSession(sessionId to sessionState.copy(rejectionError = Pair(flowError, senderDeduplicationInfo)))
                        )
                    }
                }
            }
            else -> freshErrorTransition(UnexpectedEventInState())
        }
    }

    private fun TransitionBuilder.endMessageTransition(messageIdentifier: MessageIdentifier, senderDeduplicationInfo: SenderDeduplicationInfo) {
        val sessionId = event.sessionMessage.recipientSessionId
        val sessions = currentState.checkpoint.checkpointState.sessions
        val sessionState = sessions[sessionId]
        if (sessionState == null) {
            return freshErrorTransition(CannotFindSessionException(sessionId))
        }
        when (sessionState) {
            is SessionState.Initiated -> {
                val lastDedupInfo = if (isLatestSeqNo(messageIdentifier.sessionSequenceNumber, sessionState)) {
                    senderDeduplicationInfo
                } else {
                    sessionState.lastDedupInfo
                }
                val newSessionState = sessionState.copy(toBeTerminated = Pair(messageIdentifier.sessionSequenceNumber, senderDeduplicationInfo), lastDedupInfo = lastDedupInfo)
                currentState = currentState.copy(
                        checkpoint = currentState.checkpoint.addSession(sessionId to newSessionState)
                )
            }
            else -> {
                freshErrorTransition(UnexpectedEventInState())
            }
        }
    }

}
