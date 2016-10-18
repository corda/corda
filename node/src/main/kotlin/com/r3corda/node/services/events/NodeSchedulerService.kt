package com.r3corda.node.services.events

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.SchedulableState
import com.r3corda.core.contracts.ScheduledActivity
import com.r3corda.core.contracts.ScheduledStateRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.node.services.SchedulerService
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolLogicRefFactory
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.utilities.*
import kotlinx.support.jdk8.collections.compute
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.annotation.concurrent.ThreadSafe

/**
 * A first pass of a simple [SchedulerService] that works with [MutableClock]s for testing, demonstrations and simulations
 * that also encompasses the [Vault] observer for processing transactions.
 *
 * This will observe transactions as they are stored and schedule and unschedule activities based on the States consumed
 * or produced.
 *
 * Currently does not provide any system state other than the ContractState so the expectation is that a transaction
 * is the outcome of the activity in order to schedule another activity.  Once we have implemented more persistence
 * in the nodes, maybe we can consider multiple activities and whether the activities have been completed or not,
 * but that starts to sound a lot like off-ledger state.
 *
 * @param services Core node services.
 * @param protocolLogicRefFactory Factory for restoring [ProtocolLogic] instances from references.
 * @param schedulerTimerExecutor The executor the scheduler blocks on waiting for the clock to advance to the next
 * activity.  Only replace this for unit testing purposes.  This is not the executor the [ProtocolLogic] is launched on.
 */
@ThreadSafe
class NodeSchedulerService(private val database: Database,
                           private val services: ServiceHubInternal,
                           private val protocolLogicRefFactory: ProtocolLogicRefFactory = ProtocolLogicRefFactory(),
                           private val schedulerTimerExecutor: Executor = Executors.newSingleThreadExecutor())
: SchedulerService, SingletonSerializeAsToken() {

    private val log = loggerFor<NodeSchedulerService>()

    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}scheduled_states") {
        val output = stateRef("transaction_id", "output_index")
        val scheduledAt = instant("scheduled_at")
    }

    // Variables inside InnerState are protected with a lock by the ThreadBox and aren't in scope unless you're
    // inside mutex.locked {} code block. So we can't forget to take the lock unless we accidentally leak a reference
    // to somewhere.
    private class InnerState {
        var scheduledStates = object : AbstractJDBCHashMap<StateRef, ScheduledStateRef, Table>(Table, loadOnInit = true) {
            override fun keyFromRow(row: ResultRow): StateRef = StateRef(row[table.output.txId], row[table.output.index])

            override fun valueFromRow(row: ResultRow): ScheduledStateRef {
                return ScheduledStateRef(StateRef(row[table.output.txId], row[table.output.index]), row[table.scheduledAt])
            }

            override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<StateRef, ScheduledStateRef>, finalizables: MutableList<() -> Unit>) {
                insert[table.output.txId] = entry.key.txhash
                insert[table.output.index] = entry.key.index
            }

            override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<StateRef, ScheduledStateRef>, finalizables: MutableList<() -> Unit>) {
                insert[table.scheduledAt] = entry.value.scheduledAt
            }

        }
        var earliestState: ScheduledStateRef? = null
        var rescheduled: SettableFuture<Boolean>? = null

        internal fun recomputeEarliest() {
            earliestState = scheduledStates.values.sortedBy { it.scheduledAt }.firstOrNull()
        }
    }

    private val mutex = ThreadBox(InnerState())

    // We need the [StateMachineManager] to be constructed before this is called in case it schedules a protocol.
    fun start() {
        mutex.locked {
            recomputeEarliest()
            rescheduleWakeUp()
        }
    }

    override fun scheduleStateActivity(action: ScheduledStateRef) {
        log.trace { "Schedule $action" }
        mutex.locked {
            scheduledStates[action.ref] = action
            if (action.scheduledAt.isBefore(earliestState?.scheduledAt ?: Instant.MAX)) {
                // We are earliest
                earliestState = action
                rescheduleWakeUp()
            } else if (earliestState?.ref == action.ref && earliestState!!.scheduledAt != action.scheduledAt) {
                // We were earliest but might not be any more
                recomputeEarliest()
                rescheduleWakeUp()
            }
        }
    }

    override fun unscheduleStateActivity(ref: StateRef) {
        log.trace { "Unschedule $ref" }
        mutex.locked {
            val removedAction = scheduledStates.remove(ref)
            if (removedAction == earliestState && removedAction != null) {
                recomputeEarliest()
                rescheduleWakeUp()
            }
        }
    }

    /**
     * This method first cancels the [Future] for any pending action so that the [awaitWithDeadline] used below
     * drops through without running the action.  We then create a new [Future] for the new action (so it too can be
     * cancelled), and then await the arrival of the scheduled time.  If we reach the scheduled time (the deadline)
     * without the [Future] being cancelled then we run the scheduled action.  Finally we remove that action from the
     * scheduled actions and recompute the next scheduled action.
     */
    internal fun rescheduleWakeUp() {
        // Note, we already have the mutex but we need the scope again here
        val (scheduledState, ourRescheduledFuture) = mutex.alreadyLocked {
            rescheduled?.cancel(false)
            rescheduled = SettableFuture.create()
            Pair(earliestState, rescheduled!!)
        }
        if (scheduledState != null) {
            schedulerTimerExecutor.execute() {
                log.trace { "Scheduling as next $scheduledState" }
                // This will block the scheduler single thread until the scheduled time (returns false) OR
                // the Future is cancelled due to rescheduling (returns true).
                if (!services.clock.awaitWithDeadline(scheduledState.scheduledAt, ourRescheduledFuture)) {
                    log.trace { "Invoking as next $scheduledState" }
                    onTimeReached(scheduledState)
                } else {
                    log.trace { "Recheduled $scheduledState" }
                }
            }
        }
    }

    private fun onTimeReached(scheduledState: ScheduledStateRef) {
        services.startProtocol(RunScheduled(scheduledState, this@NodeSchedulerService))
    }

    class RunScheduled(val scheduledState: ScheduledStateRef, val scheduler: NodeSchedulerService) : ProtocolLogic<Unit>() {
        companion object {
            object RUNNING : ProgressTracker.Step("Running scheduled...")

            fun tracker() = ProgressTracker(RUNNING)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): Unit {
            progressTracker.currentStep = RUNNING

            // Ensure we are still scheduled.
            val scheduledLogic: ProtocolLogic<*>? = getScheduledLogic()
            if(scheduledLogic != null) {
                subProtocol(scheduledLogic)
            }
        }

        private fun getScheduledaActivity(): ScheduledActivity? {
            val txState = serviceHub.loadState(scheduledState.ref)
            val state = txState.data as SchedulableState
            return try {
                // This can throw as running contract code.
                state.nextScheduledActivity(scheduledState.ref, scheduler.protocolLogicRefFactory)
            } catch(e: Exception) {
                logger.error("Attempt to run scheduled state $scheduledState resulted in error.", e)
                null
            }
        }

        private fun getScheduledLogic(): ProtocolLogic<*>? {
            val scheduledActivity = getScheduledaActivity()
            var scheduledLogic: ProtocolLogic<*>? = null
            scheduler.mutex.locked {
                // need to remove us from those scheduled, but only if we are still next
                scheduledStates.compute(scheduledState.ref) { ref, value ->
                    if (value === scheduledState) {
                        if (scheduledActivity == null) {
                            logger.info("Scheduled state $scheduledState has rescheduled to never.")
                            null
                        } else if (scheduledActivity.scheduledAt.isAfter(serviceHub.clock.instant())) {
                            logger.info("Scheduled state $scheduledState has rescheduled to ${scheduledActivity.scheduledAt}.")
                            ScheduledStateRef(scheduledState.ref, scheduledActivity.scheduledAt)
                        } else {
                            // TODO: ProtocolLogicRefFactory needs to sort out the class loader etc
                            val logic = scheduler.protocolLogicRefFactory.toProtocolLogic(scheduledActivity.logicRef)
                            logger.trace { "Scheduler starting ProtocolLogic $logic" }
                            // ProtocolLogic will be checkpointed by the time this returns.
                            //scheduler.services.startProtocolAndForget(logic)
                            scheduledLogic = logic
                            null
                        }
                    } else {
                        value
                    }
                }
                // and schedule the next one
                recomputeEarliest()
                scheduler.rescheduleWakeUp()
            }
            return scheduledLogic
        }
    }
}