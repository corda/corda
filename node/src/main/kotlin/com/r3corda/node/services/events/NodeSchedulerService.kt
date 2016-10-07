package com.r3corda.node.services.events

import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.SchedulableState
import com.r3corda.core.contracts.ScheduledStateRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.node.services.SchedulerService
import com.r3corda.core.protocols.ProtocolLogicRefFactory
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.utilities.awaitWithDeadline
import java.time.Instant
import java.util.*
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
 * TODO: Needs extensive support from persistence and protocol frameworks to be truly reliable and atomic.
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
class NodeSchedulerService(private val services: ServiceHubInternal,
                           private val protocolLogicRefFactory: ProtocolLogicRefFactory = ProtocolLogicRefFactory(),
                           private val schedulerTimerExecutor: Executor = Executors.newSingleThreadExecutor())
: SchedulerService, SingletonSerializeAsToken() {

    private val log = loggerFor<NodeSchedulerService>()

    // Variables inside InnerState are protected with a lock by the ThreadBox and aren't in scope unless you're
    // inside mutex.locked {} code block. So we can't forget to take the lock unless we accidentally leak a reference
    // to somewhere.
    private class InnerState {
        // TODO: This has no persistence, and we don't consider initialising from non-empty map if we add persistence.
        //       If we were to rebuild the vault at start up by replaying transactions and re-calculating, then
        //       persistence here would be unnecessary.
        var scheduledStates = HashMap<StateRef, ScheduledStateRef>()
        var earliestState: ScheduledStateRef? = null
        var rescheduled: SettableFuture<Boolean>? = null

        internal fun recomputeEarliest() {
            earliestState = scheduledStates.map { it.value }.sortedBy { it.scheduledAt }.firstOrNull()
        }
    }

    private val mutex = ThreadBox(InnerState())

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
    private fun rescheduleWakeUp() {
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
        try {
            runScheduledActionForState(scheduledState)
        } finally {
            // Unschedule once complete (or checkpointed)
            mutex.locked {
                // need to remove us from those scheduled, but only if we are still next
                scheduledStates.compute(scheduledState.ref) { ref, value ->
                    if (value === scheduledState) null else value
                }
                // and schedule the next one
                recomputeEarliest()
                rescheduleWakeUp()
            }
        }
    }

    private fun runScheduledActionForState(scheduledState: ScheduledStateRef) {
        val txState = services.loadState(scheduledState.ref)
        // It's OK to return if it's null as there's nothing scheduled
        // TODO: implement sandboxing as necessary
        val scheduledActivity = sandbox {
            val state = txState.data as SchedulableState
            state.nextScheduledActivity(scheduledState.ref, protocolLogicRefFactory)
        } ?: return

        if (scheduledActivity.scheduledAt.isAfter(services.clock.instant())) {
            // I suppose it might turn out that the action is no longer due (a bug, maybe), so we need to defend against that and re-schedule
            // TODO: warn etc
            mutex.locked {
                // Replace with updated instant
                scheduledStates.compute(scheduledState.ref) { ref, value ->
                    if (value === scheduledState) ScheduledStateRef(scheduledState.ref, scheduledActivity.scheduledAt) else value
                }
            }
        } else {
            /**
             * TODO: align with protocol invocation via API... make it the same code
             * TODO: Persistence and durability issues:
             *       a) Need to consider potential to run activity twice if restart between here and removing from map if we add persistence
             *       b) But if remove from map first, there's potential to run zero times if restart
             *       c) Address by switch to 3rd party scheduler?  Only benefit of this impl. is support for DemoClock or other MutableClocks (e.g. for testing)
             * TODO: ProtocolLogicRefFactory needs to sort out the class loader etc
             */
            val logic = protocolLogicRefFactory.toProtocolLogic(scheduledActivity.logicRef)
            log.trace { "Firing ProtocolLogic $logic" }
            // TODO: ProtocolLogic should be checkpointed by the time this returns
            services.startProtocol(logic)
        }
    }

    // TODO: Does nothing right now, but beware we are calling dynamically loaded code in the contract inside here.
    private inline fun <T : Any> sandbox(code: () -> T?): T? {
        return code()
    }
}