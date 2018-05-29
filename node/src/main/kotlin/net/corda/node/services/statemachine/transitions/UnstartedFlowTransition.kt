package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.FlowInfo
import net.corda.node.services.statemachine.*

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
        val initiatedState = SessionState.Initiated(
                peerParty = flowStart.peerSession.counterparty,
                initiatedState = InitiatedSessionState.Live(initiatingMessage.initiatorSessionId),
                peerFlowInfo = FlowInfo(
                        flowVersion = flowStart.senderCoreFlowVersion ?: initiatingMessage.flowVersion,
                        appName = initiatingMessage.appName
                ),
                receivedMessages = if (initiatingMessage.firstPayload == null) {
                    emptyList()
                } else {
                    listOf(DataSessionMessage(initiatingMessage.firstPayload))
                },
                errors = emptyList()
        )
        val confirmationMessage = ConfirmSessionMessage(flowStart.initiatedSessionId, flowStart.initiatedFlowInfo)
        val sessionMessage = ExistingSessionMessage(initiatingMessage.initiatorSessionId, confirmationMessage)
        currentState = currentState.copy(
                checkpoint = currentState.checkpoint.copy(
                        sessions = mapOf(flowStart.initiatedSessionId to initiatedState)
                )
        )
        actions.add(
                Action.SendExisting(
                        flowStart.peerSession.counterparty,
                        sessionMessage,
                        SenderDeduplicationId(DeduplicationId.createForNormal(currentState.checkpoint, 0), currentState.senderUUID)
                )
        )
    }

    // Create initial checkpoint and acknowledge triggering messages.
    private fun TransitionBuilder.createInitialCheckpoint() {
        actions.addAll(arrayOf(
                Action.CreateTransaction,
                Action.PersistCheckpoint(context.id, currentState.checkpoint),
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
