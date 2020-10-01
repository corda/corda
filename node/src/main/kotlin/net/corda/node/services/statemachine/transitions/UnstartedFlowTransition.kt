package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.FlowInfo
import net.corda.node.services.messaging.MessageIdentifier
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.ConfirmSessionMessage
import net.corda.node.services.statemachine.DataSessionMessage
import net.corda.node.services.statemachine.ExistingSessionMessage
import net.corda.node.services.statemachine.FlowStart
import net.corda.node.services.statemachine.FlowState
import net.corda.node.services.messaging.SenderDeduplicationInfo
import net.corda.node.services.statemachine.MessageType
import net.corda.node.services.statemachine.SessionState
import net.corda.node.services.statemachine.StateMachineState

/**
 * This transition is responsible for starting the flow from a FlowLogic instance. It creates the first checkpoint and
 * initialises the initiated session in case the flow is an initiated one.
 */
class UnstartedFlowTransition(
        override val context: TransitionContext,
        override val startingState: StateMachineState,
        val unstarted: FlowState.Unstarted
) : Transition {
    override fun transition(): TransitionResult {
        return builder {
            if (!currentState.isAnyCheckpointPersisted && !currentState.isStartIdempotent) {
                createInitialCheckpoint()
            }

            actions += Action.SignalFlowHasStarted(context.id)

            if (unstarted.flowStart is FlowStart.Initiated) {
                initialiseInitiatedSession(unstarted.flowStart)
            }

            currentState = currentState.copy(isFlowResumed = true)
            actions += Action.CreateTransaction
            FlowContinuation.Resume(null)
        }
    }

    // Initialise initiated session, store initial payload, send confirmation back.
    private fun TransitionBuilder.initialiseInitiatedSession(flowStart: FlowStart.Initiated) {
        val initiatingMessage = flowStart.initiatingMessage
        val initiatedState = SessionState.Initiated(
                peerParty = flowStart.peerSession.counterparty,
                peerSinkSessionId = initiatingMessage.initiatorSessionId,
                peerFlowInfo = FlowInfo(
                        flowVersion = flowStart.senderCoreFlowVersion ?: initiatingMessage.flowVersion,
                        appName = initiatingMessage.appName
                ),
                receivedMessages = if (initiatingMessage.firstPayload == null) {
                    emptyMap()
                } else {
                    mapOf(0 to DataSessionMessage(initiatingMessage.firstPayload))
                },
                otherSideErrored = false,
                nextSendingSeqNumber = 1,
                lastProcessedSeqNumber = if (initiatingMessage.firstPayload == null) {
                    0
                } else {
                    -1
                },
                shardId = flowStart.shardIdentifier,
                lastSenderUUID = flowStart.senderUUID,
                lastSenderSeqNo = flowStart.senderSequenceNumber
        )
        val confirmationMessage = ConfirmSessionMessage(flowStart.initiatedSessionId, flowStart.initiatedFlowInfo)
        val sessionMessage = ExistingSessionMessage(initiatingMessage.initiatorSessionId, confirmationMessage)
        val messageType = MessageType.inferFromMessage(sessionMessage)
        val messageIdentifier = MessageIdentifier(messageType, flowStart.shardIdentifier, initiatingMessage.initiatorSessionId, 0, currentState.checkpoint.lastModificationTime)
        currentState = currentState.copy(checkpoint = currentState.checkpoint.setSessions(mapOf(flowStart.initiatedSessionId to initiatedState)))
        actions.add(Action.SendExisting(flowStart.peerSession.counterparty, sessionMessage, SenderDeduplicationInfo(messageIdentifier, currentState.senderUUID)))
    }

    // Create initial checkpoint and acknowledge triggering messages.
    private fun TransitionBuilder.createInitialCheckpoint() {
        currentState = startingState.copy(
            pendingDeduplicationHandlers = emptyList(),
            isAnyCheckpointPersisted = true
        )
        actions += Action.CreateTransaction
        actions += Action.PersistCheckpoint(context.id, startingState.checkpoint, isCheckpointUpdate = startingState.isAnyCheckpointPersisted)
        actions += Action.PersistDeduplicationFacts(startingState.pendingDeduplicationHandlers)
        actions += Action.CommitTransaction(currentState)
        actions += Action.AcknowledgeMessages(startingState.pendingDeduplicationHandlers)
    }
}
