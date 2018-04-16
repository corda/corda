package net.corda.node.services.statemachine.transitions

import net.corda.core.flows.InitiatingFlow
import net.corda.core.internal.FlowIORequest
import net.corda.core.utilities.Try
import net.corda.node.services.statemachine.*

/**
 * This is the top level event-handling transition function capable of handling any [Event].
 *
 * It is a *pure* function taking a state machine state and an event, returning the next state along with a list of IO
 * actions to execute.
 */
class TopLevelTransition(
        override val context: TransitionContext,
        override val startingState: StateMachineState,
        val event: Event
) : Transition {
    override fun transition(): TransitionResult {
        return when (event) {
                is Event.DoRemainingWork -> DoRemainingWorkTransition(context, startingState).transition()
                is Event.DeliverSessionMessage -> DeliverSessionMessageTransition(context, startingState, event).transition()
                is Event.Error -> errorTransition(event)
                is Event.TransactionCommitted -> transactionCommittedTransition(event)
                is Event.SoftShutdown -> softShutdownTransition()
                is Event.StartErrorPropagation -> startErrorPropagationTransition()
                is Event.EnterSubFlow -> enterSubFlowTransition(event)
                is Event.LeaveSubFlow -> leaveSubFlowTransition()
                is Event.Suspend -> suspendTransition(event)
                is Event.FlowFinish -> flowFinishTransition(event)
                is Event.InitiateFlow -> initiateFlowTransition(event)
                is Event.AsyncOperationCompletion -> asyncOperationCompletionTransition(event)
        }
    }

    private fun errorTransition(event: Event.Error): TransitionResult {
        return builder {
            freshErrorTransition(event.exception)
            FlowContinuation.ProcessEvents
        }
    }

    private fun transactionCommittedTransition(event: Event.TransactionCommitted): TransitionResult {
        return builder {
            val checkpoint = currentState.checkpoint
            if (currentState.isTransactionTracked &&
                    checkpoint.flowState is FlowState.Started &&
                    checkpoint.flowState.flowIORequest is FlowIORequest.WaitForLedgerCommit &&
                    checkpoint.flowState.flowIORequest.hash == event.transaction.id) {
                currentState = currentState.copy(isTransactionTracked = false)
                if (isErrored()) {
                    return@builder FlowContinuation.ProcessEvents
                }
                resumeFlowLogic(event.transaction)
            } else {
                freshErrorTransition(UnexpectedEventInState())
                FlowContinuation.ProcessEvents
            }
        }
    }

    private fun softShutdownTransition(): TransitionResult {
        val lastState = startingState.copy(isRemoved = true)
        return TransitionResult(
                newState = lastState,
                actions = listOf(
                        Action.RemoveSessionBindings(startingState.checkpoint.sessions.keys),
                        Action.RemoveFlow(context.id, FlowRemovalReason.SoftShutdown, lastState)
                ),
                continuation = FlowContinuation.Abort
        )
    }

    private fun startErrorPropagationTransition(): TransitionResult {
        return builder {
            val errorState = currentState.checkpoint.errorState
            when (errorState) {
                ErrorState.Clean -> freshErrorTransition(UnexpectedEventInState())
                is ErrorState.Errored -> {
                    currentState = currentState.copy(
                            checkpoint = currentState.checkpoint.copy(
                                    errorState = errorState.copy(propagating = true)
                            )
                    )
                    actions.add(Action.ScheduleEvent(Event.DoRemainingWork))
                }
            }
            FlowContinuation.ProcessEvents
        }
    }

    private fun enterSubFlowTransition(event: Event.EnterSubFlow): TransitionResult {
        return builder {
            val subFlow = SubFlow.create(event.subFlowClass)
            when (subFlow) {
                is Try.Success -> {
                    currentState = currentState.copy(
                            checkpoint = currentState.checkpoint.copy(
                                    subFlowStack = currentState.checkpoint.subFlowStack + subFlow.value
                            )
                    )
                }
                is Try.Failure -> {
                    freshErrorTransition(subFlow.exception)
                }
            }
            FlowContinuation.ProcessEvents
        }
    }

    private fun leaveSubFlowTransition(): TransitionResult {
        return builder {
            val checkpoint = currentState.checkpoint
            if (checkpoint.subFlowStack.isEmpty()) {
                freshErrorTransition(UnexpectedEventInState())
            } else {
                currentState = currentState.copy(
                        checkpoint = checkpoint.copy(
                                subFlowStack = checkpoint.subFlowStack.subList(0, checkpoint.subFlowStack.size - 1).toList()
                        )
                )
            }
            FlowContinuation.ProcessEvents
        }
    }

    private fun suspendTransition(event: Event.Suspend): TransitionResult {
        return builder {
            val newCheckpoint = currentState.checkpoint.copy(
                    flowState = FlowState.Started(event.ioRequest, event.fiber),
                    numberOfSuspends = currentState.checkpoint.numberOfSuspends + 1
            )
            actions.addAll(arrayOf(
                    Action.PersistCheckpoint(context.id, newCheckpoint),
                    Action.PersistDeduplicationFacts(currentState.pendingDeduplicationHandlers),
                    Action.CommitTransaction,
                    Action.AcknowledgeMessages(currentState.pendingDeduplicationHandlers),
                    Action.ScheduleEvent(Event.DoRemainingWork)
            ))
            currentState = currentState.copy(
                    checkpoint = newCheckpoint,
                    pendingDeduplicationHandlers = emptyList(),
                    isFlowResumed = false,
                    isAnyCheckpointPersisted = true
            )
            FlowContinuation.ProcessEvents
        }
    }

    private fun flowFinishTransition(event: Event.FlowFinish): TransitionResult {
        return builder {
            val checkpoint = currentState.checkpoint
            when (checkpoint.errorState) {
                ErrorState.Clean -> {
                    val pendingDeduplicationHandlers = currentState.pendingDeduplicationHandlers
                    currentState = currentState.copy(
                            checkpoint = checkpoint.copy(
                                    numberOfSuspends = checkpoint.numberOfSuspends + 1
                            ),
                            pendingDeduplicationHandlers = emptyList(),
                            isFlowResumed = false,
                            isRemoved = true
                    )
                    val allSourceSessionIds = checkpoint.sessions.keys
                    if (currentState.isAnyCheckpointPersisted) {
                        actions.add(Action.RemoveCheckpoint(context.id))
                    }
                    actions.addAll(arrayOf(
                            Action.PersistDeduplicationFacts(pendingDeduplicationHandlers),
                            Action.CommitTransaction,
                            Action.AcknowledgeMessages(pendingDeduplicationHandlers),
                            Action.RemoveSessionBindings(allSourceSessionIds),
                            Action.RemoveFlow(context.id, FlowRemovalReason.OrderlyFinish(event.returnValue), currentState)
                    ))
                    sendEndMessages()
                    // Resume to end fiber
                    FlowContinuation.Resume(null)
                }
                is ErrorState.Errored -> {
                    currentState = currentState.copy(isFlowResumed = false)
                    actions.add(Action.RollbackTransaction)
                    FlowContinuation.ProcessEvents
                }
            }
        }
    }

    private fun TransitionBuilder.sendEndMessages() {
        val sendEndMessageActions = currentState.checkpoint.sessions.values.mapIndexed { index, state ->
            if (state is SessionState.Initiated && state.initiatedState is InitiatedSessionState.Live) {
                val message = ExistingSessionMessage(state.initiatedState.peerSinkSessionId, EndSessionMessage)
                val deduplicationId = DeduplicationId.createForNormal(currentState.checkpoint, index)
                Action.SendExisting(state.peerParty, message, deduplicationId)
            } else {
                null
            }
        }.filterNotNull()
        actions.addAll(sendEndMessageActions)
    }

    private fun initiateFlowTransition(event: Event.InitiateFlow): TransitionResult {
        return builder {
            val checkpoint = currentState.checkpoint
            val initiatingSubFlow = getClosestAncestorInitiatingSubFlow(checkpoint)
            if (initiatingSubFlow == null) {
                freshErrorTransition(IllegalStateException("Tried to initiate in a flow not annotated with @${InitiatingFlow::class.java.simpleName}"))
                return@builder FlowContinuation.ProcessEvents
            }
            val sourceSessionId = SessionId.createRandom(context.secureRandom)
            val sessionImpl = FlowSessionImpl(event.party, sourceSessionId)
            val newSessions = checkpoint.sessions + (sourceSessionId to SessionState.Uninitiated(event.party, initiatingSubFlow))
            currentState = currentState.copy(checkpoint = checkpoint.copy(sessions = newSessions))
            actions.add(Action.AddSessionBinding(context.id, sourceSessionId))
            FlowContinuation.Resume(sessionImpl)
        }
    }

    private fun getClosestAncestorInitiatingSubFlow(checkpoint: Checkpoint): SubFlow.Initiating? {
        for (subFlow in checkpoint.subFlowStack.asReversed()) {
            if (subFlow is SubFlow.Initiating) {
                return subFlow
            }
        }
        return null
    }

    private fun asyncOperationCompletionTransition(event: Event.AsyncOperationCompletion): TransitionResult {
        return builder {
            resumeFlowLogic(event.returnValue)
        }
    }
}
