package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.nodeapi.internal.persistence.contextDatabase
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * This is the bottom execution engine of flow side-effects.
 */
class ActionExecutorImpl(
        private val services: ServiceHubInternal,
        private val checkpointStorage: CheckpointStorage,
        private val flowMessaging: FlowMessaging,
        private val stateMachineManager: StateMachineManagerInternal,
        private val checkpointSerializationContext: SerializationContext
) : ActionExecutor {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun executeAction(fiber: FlowFiber, action: Action) {
        log.trace { "Flow ${fiber.id} executing $action" }
        return when (action) {
            is Action.TrackTransaction -> executeTrackTransaction(fiber, action)
            is Action.PersistCheckpoint -> executePersistCheckpoint(action)
            is Action.PersistDeduplicationIds -> executePersistDeduplicationIds(action)
            is Action.AcknowledgeMessages -> executeAcknowledgeMessages(action)
            is Action.PropagateErrors -> executePropagateErrors(action)
            is Action.ScheduleEvent -> executeScheduleEvent(fiber, action)
            is Action.SleepUntil -> executeSleepUntil(action)
            is Action.RemoveCheckpoint -> executeRemoveCheckpoint(action)
            is Action.SendInitial -> executeSendInitial(action)
            is Action.SendExisting -> executeSendExisting(action)
            is Action.AddSessionBinding -> executeAddSessionBinding(action)
            is Action.RemoveSessionBindings -> executeRemoveSessionBindings(action)
            is Action.SignalFlowHasStarted -> executeSignalFlowHasStarted(action)
            is Action.RemoveFlow -> executeRemoveFlow(action)
            is Action.CreateTransaction -> executeCreateTransaction()
            is Action.RollbackTransaction -> executeRollbackTransaction()
            is Action.CommitTransaction -> executeCommitTransaction()
        }
    }

    @Suspendable
    private fun executeTrackTransaction(fiber: FlowFiber, action: Action.TrackTransaction) {
        services.validatedTransactions.trackTransaction(action.hash).thenMatch(
                success = { transaction ->
                    fiber.scheduleEvent(Event.TransactionCommitted(transaction))
                },
                failure = { exception ->
                    fiber.scheduleEvent(Event.Error(exception))
                }
        )
    }

    @Suspendable
    private fun executePersistCheckpoint(action: Action.PersistCheckpoint) {
        val checkpointBytes = serializeCheckpoint(action.checkpoint)
        checkpointStorage.addCheckpoint(action.id, checkpointBytes)
    }

    @Suspendable
    private fun executePersistDeduplicationIds(action: Action.PersistDeduplicationIds) {
        for (handle in action.acknowledgeHandles) {
            handle.persistDeduplicationId()
        }
    }

    @Suspendable
    private fun executeAcknowledgeMessages(action: Action.AcknowledgeMessages) {
        action.acknowledgeHandles.forEach {
            it.acknowledge()
        }
    }

    @Suspendable
    private fun executePropagateErrors(action: Action.PropagateErrors) {
        action.errorMessages.forEach { error ->
            val exception = error.flowException
            log.debug("Propagating error", exception)
        }
        val pendingSendAcks = CountUpDownLatch(0)
        for (sessionState in action.sessions) {
            // We cannot propagate if the session isn't live.
            if (sessionState.initiatedState !is InitiatedSessionState.Live) {
                continue
            }
            // Don't propagate errors to the originating session
            for (errorMessage in action.errorMessages) {
                val sinkSessionId = sessionState.initiatedState.peerSinkSessionId
                val existingMessage = ExistingSessionMessage(sinkSessionId, errorMessage)
                val deduplicationId = DeduplicationId.createForError(errorMessage.errorId, sinkSessionId)
                pendingSendAcks.countUp()
                flowMessaging.sendSessionMessage(sessionState.peerParty, existingMessage, deduplicationId) {
                    pendingSendAcks.countDown()
                }
            }
        }
        // TODO we simply block here, perhaps this should be explicit in the worker state
        pendingSendAcks.await()
    }

    @Suspendable
    private fun executeScheduleEvent(fiber: FlowFiber, action: Action.ScheduleEvent) {
        fiber.scheduleEvent(action.event)
    }

    @Suspendable
    private fun executeSleepUntil(action: Action.SleepUntil) {
        // TODO introduce explicit sleep state + wakeup event instead of relying on Fiber.sleep. This is so shutdown
        // conditions may "interrupt" the sleep instead of waiting until wakeup.
        val duration = Duration.between(Instant.now(), action.time)
        Fiber.sleep(duration.toNanos(), TimeUnit.NANOSECONDS)
    }

    @Suspendable
    private fun executeRemoveCheckpoint(action: Action.RemoveCheckpoint) {
        checkpointStorage.removeCheckpoint(action.id)
    }

    @Suspendable
    private fun executeSendInitial(action: Action.SendInitial) {
        flowMessaging.sendSessionMessage(action.party, action.initialise, action.deduplicationId, null)
    }

    @Suspendable
    private fun executeSendExisting(action: Action.SendExisting) {
        flowMessaging.sendSessionMessage(action.peerParty, action.message, action.deduplicationId, null)
    }

    @Suspendable
    private fun executeAddSessionBinding(action: Action.AddSessionBinding) {
        stateMachineManager.addSessionBinding(action.flowId, action.sessionId)
    }

    @Suspendable
    private fun executeRemoveSessionBindings(action: Action.RemoveSessionBindings) {
        stateMachineManager.removeSessionBindings(action.sessionIds)
    }

    @Suspendable
    private fun executeSignalFlowHasStarted(action: Action.SignalFlowHasStarted) {
        stateMachineManager.signalFlowHasStarted(action.flowId)
    }

    @Suspendable
    private fun executeRemoveFlow(action: Action.RemoveFlow) {
        stateMachineManager.removeFlow(action.flowId, action.removalReason, action.lastState)
    }

    @Suspendable
    private fun executeCreateTransaction() {
        if (contextTransactionOrNull != null) {
            throw IllegalStateException("Refusing to create a second transaction")
        }
        contextDatabase.newTransaction()
    }

    @Suspendable
    private fun executeRollbackTransaction() {
        contextTransactionOrNull?.close()
    }

    @Suspendable
    private fun executeCommitTransaction() {
        try {
            contextTransaction.commit()
        } finally {
            contextTransaction.close()
            contextTransactionOrNull = null
        }
    }

    private fun serializeCheckpoint(checkpoint: Checkpoint): SerializedBytes<Checkpoint> {
        return checkpoint.serialize(context = checkpointSerializationContext)
    }
}
