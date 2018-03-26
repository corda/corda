package net.corda.node.services.events

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledActivity
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.join
import net.corda.core.internal.until
import net.corda.core.node.ServicesForResolution
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.CordaClock
import net.corda.node.MutableClock
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.api.SchedulerService
import net.corda.node.utilities.PersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import co.paralleluniverse.strands.SettableFuture as QuasarSettableFuture
import com.google.common.util.concurrent.SettableFuture as GuavaSettableFuture

/**
 * A first pass of a simple [SchedulerService] that works with [MutableClock]s for testing, demonstrations and simulations
 * that also encompasses the [net.corda.core.node.services.Vault] observer for processing transactions.
 *
 * This will observe transactions as they are stored and schedule and unschedule activities based on the States consumed
 * or produced.
 *
 * Currently does not provide any system state other than the ContractState so the expectation is that a transaction
 * is the outcome of the activity in order to schedule another activity.  Once we have implemented more persistence
 * in the nodes, maybe we can consider multiple activities and whether the activities have been completed or not,
 * but that starts to sound a lot like off-ledger state.
 */
@ThreadSafe
class NodeSchedulerService(private val clock: CordaClock,
                           private val database: CordaPersistence,
                           private val flowStarter: FlowStarter,
                           private val servicesForResolution: ServicesForResolution,
                           private val unfinishedSchedules: ReusableLatch = ReusableLatch(),
                           private val serverThread: Executor,
                           private val flowLogicRefFactory: FlowLogicRefFactory,
                           private val nodeProperties: NodePropertiesStore,
                           private val drainingModePollPeriod: Duration,
                           private val log: Logger = staticLog,
                           private val scheduledStates: MutableMap<StateRef, ScheduledStateRef> = createMap())
    : SchedulerService, SingletonSerializeAsToken() {

    companion object {
        private val staticLog get() = contextLogger()
        /**
         * Wait until the given [Future] is complete or the deadline is reached, with support for [MutableClock] implementations
         * used in demos or testing.  This will substitute a Fiber compatible Future so the current
         * [co.paralleluniverse.strands.Strand] is not blocked.
         *
         * @return true if the [Future] is complete, false if the deadline was reached.
         */
        // We should try to make the Clock used in our code injectable (for tests etc) and to use the extension below
        // to wait in our code, rather than <code>Thread.sleep()</code> or other time-based pauses.
        @Suspendable
        @VisibleForTesting
                // We specify full classpath on SettableFuture to differentiate it from the Quasar class of the same name
        fun awaitWithDeadline(clock: CordaClock, deadline: Instant, future: Future<*> = GuavaSettableFuture.create<Any>()): Boolean {
            var nanos: Long
            do {
                val originalFutureCompleted = makeStrandFriendlySettableFuture(future)
                val subscription = clock.mutations.first().subscribe {
                    originalFutureCompleted.set(false)
                }
                nanos = (clock.instant() until deadline).toNanos()
                if (nanos > 0) {
                    try {
                        // This will return when it times out, or when the clock mutates or when when the original future completes.
                        originalFutureCompleted.get(nanos, TimeUnit.NANOSECONDS)
                    } catch (e: ExecutionException) {
                        // No need to take action as will fall out of the loop due to future.isDone
                    } catch (e: CancellationException) {
                        // No need to take action as will fall out of the loop due to future.isDone
                    } catch (e: TimeoutException) {
                        // No need to take action as will fall out of the loop due to future.isDone
                    }
                }
                subscription.unsubscribe()
                originalFutureCompleted.cancel(false)
            } while (nanos > 0 && !future.isDone)
            return future.isDone
        }

        fun createMap(): PersistentMap<StateRef, ScheduledStateRef, PersistentScheduledState, PersistentStateRef> {
            return PersistentMap(
                    toPersistentEntityKey = { PersistentStateRef(it.txhash.toString(), it.index) },
                    fromPersistentEntity = {
                        //TODO null check will become obsolete after making DB/JPA columns not nullable
                        val txId = it.output.txId ?: throw IllegalStateException("DB returned null SecureHash transactionId")
                        val index = it.output.index ?: throw IllegalStateException("DB returned null SecureHash index")
                        Pair(StateRef(SecureHash.parse(txId), index),
                                ScheduledStateRef(StateRef(SecureHash.parse(txId), index), it.scheduledAt))
                    },
                    toPersistentEntity = { key: StateRef, value: ScheduledStateRef ->
                        PersistentScheduledState().apply {
                            output = PersistentStateRef(key.txhash.toString(), key.index)
                            scheduledAt = value.scheduledAt
                        }
                    },
                    persistentEntityClass = PersistentScheduledState::class.java
            )
        }

        /**
         * Convert a Guava [ListenableFuture] or JDK8 [CompletableFuture] to Quasar implementation and set to true when a result
         * or [Throwable] is available in the original.
         *
         * We need this so that we do not block the actual thread when calling get(), but instead allow a Quasar context
         * switch.  There's no need to checkpoint our [co.paralleluniverse.fibers.Fiber]s as there's no external effect of waiting.
         */
        private fun <T : Any> makeStrandFriendlySettableFuture(future: Future<T>) = QuasarSettableFuture<Boolean>().also { g ->
            when (future) {
                is ListenableFuture -> future.addListener(Runnable { g.set(true) }, Executor { it.run() })
                is CompletionStage<*> -> future.whenComplete { _, _ -> g.set(true) }
                else -> throw IllegalArgumentException("Cannot make future $future Strand friendly.")
            }
        }

        @VisibleForTesting
        internal val schedulingAsNextFormat = "Scheduling as next {}"
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}scheduled_states")
    class PersistentScheduledState(
            @EmbeddedId
            var output: PersistentStateRef = PersistentStateRef(),

            @Column(name = "scheduled_at", nullable = false)
            var scheduledAt: Instant = Instant.now()
    )

    private class InnerState {
        var scheduledStatesQueue: PriorityQueue<ScheduledStateRef> = PriorityQueue({ a, b -> a.scheduledAt.compareTo(b.scheduledAt) })

        var rescheduled: GuavaSettableFuture<Boolean>? = null
    }

    private val mutex = ThreadBox(InnerState())
    // We need the [StateMachineManager] to be constructed before this is called in case it schedules a flow.
    fun start() {
        mutex.locked {
            scheduledStatesQueue.addAll(scheduledStates.values)
            rescheduleWakeUp()
        }
    }

    override fun scheduleStateActivity(action: ScheduledStateRef) {
        log.trace { "Schedule $action" }
        val previousState = scheduledStates[action.ref]
        scheduledStates[action.ref] = action
        mutex.locked {
            val previousEarliest = scheduledStatesQueue.peek()
            scheduledStatesQueue.remove(previousState)
            scheduledStatesQueue.add(action)
            if (previousState == null) {
                unfinishedSchedules.countUp()
            }

            if (action.scheduledAt.isBefore(previousEarliest?.scheduledAt ?: Instant.MAX)) {
                // We are earliest
                rescheduleWakeUp()
            } else if (previousEarliest?.ref == action.ref && previousEarliest.scheduledAt != action.scheduledAt) {
                // We were earliest but might not be any more
                rescheduleWakeUp()
            }
        }
    }

    override fun unscheduleStateActivity(ref: StateRef) {
        log.trace { "Unschedule $ref" }
        val removedAction = scheduledStates.remove(ref)
        mutex.locked {
            if (removedAction != null) {
                val wasNext = (removedAction == scheduledStatesQueue.peek())
                val wasRemoved = scheduledStatesQueue.remove(removedAction)
                if (wasRemoved) {
                    unfinishedSchedules.countDown()
                }
                if (wasNext) {
                    rescheduleWakeUp()
                }
            }
        }
    }

    private val schedulerTimerExecutor = Executors.newSingleThreadExecutor()
    /**
     * This method first cancels the [java.util.concurrent.Future] for any pending action so that the
     * [awaitWithDeadline] used below drops through without running the action.  We then create a new
     * [java.util.concurrent.Future] for the new action (so it too can be cancelled), and then await the arrival of the
     * scheduled time.  If we reach the scheduled time (the deadline) without the [java.util.concurrent.Future] being
     * cancelled then we run the scheduled action.  Finally we remove that action from the scheduled actions and
     * recompute the next scheduled action.
     */
    private fun rescheduleWakeUp() {
        // Note, we already have the mutex but we need the scope again here
        val (scheduledState, ourRescheduledFuture) = mutex.alreadyLocked {
            rescheduled?.cancel(false)
            rescheduled = GuavaSettableFuture.create()
            Pair(scheduledStatesQueue.peek(), rescheduled!!)
        }
        if (scheduledState != null) {
            schedulerTimerExecutor.execute {
                log.trace(schedulingAsNextFormat, scheduledState)
                // This will block the scheduler single thread until the scheduled time (returns false) OR
                // the Future is cancelled due to rescheduling (returns true).
                if (!awaitWithDeadline(clock, scheduledState.scheduledAt, ourRescheduledFuture)) {
                    log.trace { "Invoking as next $scheduledState" }
                    onTimeReached(scheduledState)
                } else {
                    log.trace { "Rescheduled $scheduledState" }
                }
            }
        }
    }

    @VisibleForTesting
    internal fun join() {
        schedulerTimerExecutor.join()
    }

    @VisibleForTesting
    internal fun cancelAndWait() {
        schedulerTimerExecutor.shutdownNow()
        schedulerTimerExecutor.join()
    }

    private fun onTimeReached(scheduledState: ScheduledStateRef) {
        serverThread.execute {
            var flowName: String? = "(unknown)"
            try {
                database.transaction {
                    val scheduledFlow = getScheduledFlow(scheduledState)
                    if (scheduledFlow != null) {
                        flowName = scheduledFlow.javaClass.name
                        // TODO refactor the scheduler to store and propagate the original invocation context
                        val context = InvocationContext.newInstance(InvocationOrigin.Scheduled(scheduledState))
                        val future = flowStarter.startFlow(scheduledFlow, context).flatMap { it.resultFuture }
                        future.then {
                            unfinishedSchedules.countDown()
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to start scheduled flow $flowName for $scheduledState due to an internal error", e)
            }
        }
    }

    private fun getScheduledFlow(scheduledState: ScheduledStateRef): FlowLogic<*>? {
        val scheduledActivity = getScheduledActivity(scheduledState)
        var scheduledFlow: FlowLogic<*>? = null
        mutex.locked {
            // need to remove us from those scheduled, but only if we are still next
            val previousState = scheduledStates[scheduledState.ref]
            if (previousState != null && previousState === scheduledState) {
                if (scheduledActivity == null) {
                    log.info("Scheduled state $scheduledState has rescheduled to never.")
                    unfinishedSchedules.countDown()
                    scheduledStates.remove(scheduledState.ref)
                    scheduledStatesQueue.remove(scheduledState)
                } else if (scheduledActivity.scheduledAt.isAfter(clock.instant())) {
                    log.info("Scheduled state $scheduledState has rescheduled to ${scheduledActivity.scheduledAt}.")
                    val newState = ScheduledStateRef(scheduledState.ref, scheduledActivity.scheduledAt)
                    scheduledStates[scheduledState.ref] = newState
                    scheduledStatesQueue.remove(scheduledState)
                    scheduledStatesQueue.add(newState)
                } else {
                    val flowLogic = flowLogicRefFactory.toFlowLogic(scheduledActivity.logicRef)
                    scheduledFlow = when {
                        nodeProperties.flowsDrainingMode.isEnabled() -> {
                            log.warn("Ignoring scheduled flow start because of draining mode. FlowLogic: $flowLogic.")
                            awaitWithDeadline(clock, Instant.now() + drainingModePollPeriod)
                            null
                        }
                        else -> {
                            log.trace { "Scheduler starting FlowLogic $flowLogic" }
                            scheduledStates.remove(scheduledState.ref)
                            scheduledStatesQueue.remove(scheduledState)
                            flowLogic
                        }
                    }
                }
            }
            // and schedule the next one
            rescheduleWakeUp()
        }
        return scheduledFlow
    }

    private fun getScheduledActivity(scheduledState: ScheduledStateRef): ScheduledActivity? {
        val txState = servicesForResolution.loadState(scheduledState.ref)
        val state = txState.data as SchedulableState
        return try {
            // This can throw as running contract code.
            state.nextScheduledActivity(scheduledState.ref, flowLogicRefFactory)
        } catch (e: Exception) {
            log.error("Attempt to run scheduled state $scheduledState resulted in error.", e)
            null
        }
    }
}