package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.FlowInfo
import net.corda.node.services.statemachine.Action
import net.corda.node.services.statemachine.ConfirmSessionMessage
import net.corda.node.services.statemachine.DataSessionMessage
import net.corda.node.services.statemachine.ExistingSessionMessage
import net.corda.node.services.statemachine.FlowStart
import net.corda.node.services.statemachine.FlowState
import net.corda.node.services.statemachine.InitiatedSessionState
import net.corda.node.services.statemachine.MessageIdentifier
import net.corda.node.services.statemachine.MessageWithDedupInfo
import net.corda.node.services.statemachine.SenderDeduplicationId
import net.corda.node.services.statemachine.SessionState
import net.corda.node.services.statemachine.StateMachineState
import java.util.*

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

            actions.add(Action.SignalFlowHasStarted(context.id))

            if (unstarted.flowStart is FlowStart.Initiated) {
                initialiseInitiatedSession(unstarted.flowStart)
            }

            currentState = currentState.copy(isFlowResumed = true)
            actions.add(Action.CreateTransaction)
            FlowContinuation.Resume(null)
        }
    }

    // Initialise initiated session, store initial payload, send confirmation back.
    private fun TransitionBuilder.initialiseInitiatedSession(flowStart: FlowStart.Initiated) {
        val initiatingMessage = flowStart.initiatingMessage
        val receivedMessages = TreeMap<Int, MessageWithDedupInfo>()
        if (initiatingMessage.firstPayload != null) {
            receivedMessages[0] = Pair(DataSessionMessage(initiatingMessage.firstPayload), flowStart.senderDedupInfo)
        }
        val initiatedState = SessionState.Initiated(
                peerParty = flowStart.peerSession.counterparty,
                initiatedState = InitiatedSessionState.Live(initiatingMessage.initiatorSessionId),
                peerFlowInfo = FlowInfo(
                        flowVersion = flowStart.senderCoreFlowVersion ?: initiatingMessage.flowVersion,
                        appName = initiatingMessage.appName
                ),
                receivedMessages = receivedMessages,
                errors = TreeMap(),
                toBeTerminated = null,
                sequenceNumber = 1,
                lastSequenceNumberProcessed = if (initiatingMessage.firstPayload == null) {
                    0
                } else {
                    -1
                },
                deduplicationSeed = "D-${initiatingMessage.initiatorSessionId.toLong}-${initiatingMessage.initiationEntropy}",
                shardId = flowStart.shardId,
                lastDedupInfo = flowStart.senderDedupInfo
        )
        val confirmationMessage = ConfirmSessionMessage(flowStart.initiatedSessionId, flowStart.initiatedFlowInfo)
        val sessionMessage = ExistingSessionMessage(initiatingMessage.initiatorSessionId, confirmationMessage)
        val messageIdentifier = MessageIdentifier("XC", flowStart.shardId, initiatingMessage.initiatorSessionId.toLong, 0, startingState.checkpoint.checkpointState.suspensionTime)
        currentState = currentState.copy(
                checkpoint = currentState.checkpoint.setSessions(mapOf(flowStart.initiatedSessionId to initiatedState))
        )
        actions.add(
                Action.SendExisting(
                        flowStart.peerSession.counterparty,
                        sessionMessage,
                        SenderDeduplicationId(messageIdentifier, currentState.senderUUID)
                )
        )
    }

    // Create initial checkpoint and acknowledge triggering messages.
    private fun TransitionBuilder.createInitialCheckpoint() {
        actions.addAll(arrayOf(
                Action.CreateTransaction,
                Action.PersistCheckpoint(context.id, currentState.checkpoint, isCheckpointUpdate = currentState.isAnyCheckpointPersisted),
                Action.PersistDeduplicationFacts(currentState.pendingDeduplicationHandlers),
                Action.CommitTransaction,
                Action.AcknowledgeMessages(currentState.pendingDeduplicationHandlers)
        ))
        currentState = currentState.copy(
                pendingDeduplicationHandlers = emptyList(),
                isAnyCheckpointPersisted = true
        )
    }
}
