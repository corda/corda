package net.corda.node.services.events

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledActivity
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.join
import net.corda.core.internal.until
import net.corda.core.node.ServicesForResolution
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.core.utilities.trace
import net.corda.node.CordaClock
import net.corda.node.MutableClock
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.api.SchedulerService
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.contextTransaction
import org.apache.activemq.artemis.utils.ReusableLatch
import org.apache.mina.util.ConcurrentHashSet
import org.slf4j.Logger
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
                           private val flowLogicRefFactory: FlowLogicRefFactory,
                           private val nodeProperties: NodePropertiesStore,
                           private val drainingModePollPeriod: Duration,
                           private val log: Logger = staticLog,
                           private val schedulerRepo: ScheduledFlowRepository = PersistentScheduledFlowRepository(database))
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
            var output: PersistentStateRef,

            @Column(name = "scheduled_at", nullable = false)
            var scheduledAt: Instant = Instant.now()
    ) : Serializable

    private class InnerState {
        var rescheduled: GuavaSettableFuture<Boolean>? = null
        var nextScheduledAction: ScheduledStateRef? = null
        var running: Boolean = true
    }

    // Used to de-duplicate flow starts in case a flow is starting but the corresponding entry hasn't been removed yet
    // from the database
    private val startingStateRefs = ConcurrentHashSet<ScheduledStateRef>()
    private val mutex = ThreadBox(InnerState())
    private val schedulerTimerExecutor = Executors.newSingleThreadExecutor()

    // if there's nothing to do, check every minute if something fell through the cracks.
    // any new state should trigger a reschedule immediately if nothing is scheduled, so I would not expect
    // this to usually trigger anything.
    private val idleWaitSeconds = 60.seconds

    // We need the [StateMachineManager] to be constructed before this is called in case it schedules a flow.
    fun start() {
        schedulerTimerExecutor.execute { runLoopFunction() }
    }

    override fun scheduleStateActivity(action: ScheduledStateRef) {
        log.trace { "Schedule $action" }
        // Only increase the number of unfinished schedules if the state didn't already exist on the queue
        if (!schedulerRepo.merge(action)) {
            unfinishedSchedules.countUp()
        }
        contextTransaction.onCommit {
            mutex.locked {
                if (action.scheduledAt < nextScheduledAction?.scheduledAt ?: Instant.MAX) {
                    // We are earliest
                    rescheduleWakeUp()
                } else if (action.ref == nextScheduledAction?.ref && action.scheduledAt != nextScheduledAction?.scheduledAt) {
                    // We were earliest but might not be any more
                    rescheduleWakeUp()
                }
            }
        }
    }

    override fun unscheduleStateActivity(ref: StateRef) {
        log.trace { "Unschedule $ref" }
        val countDown = startingStateRefs.all { it.ref != ref } && schedulerRepo.delete(ref)
        contextTransaction.onCommit {
            if (countDown) unfinishedSchedules.countDown()
            mutex.locked {
                if (nextScheduledAction?.ref == ref) {
                    rescheduleWakeUp()
                }
            }
        }
    }

    private fun runLoopFunction() {
        while (mutex.locked { running }) {
            val (scheduledState, ourRescheduledFuture) = mutex.locked {
                rescheduled = GuavaSettableFuture.create()
                //get the next scheduled action that isn't currently running
                val deduplicate = HashSet(startingStateRefs) // Take an immutable copy to remove races with afterDatabaseCommit.
                nextScheduledAction = schedulerRepo.getLatest(deduplicate.size + 1).firstOrNull { !deduplicate.contains(it.second) }?.second
                Pair(nextScheduledAction, rescheduled!!)
            }
            log.trace(schedulingAsNextFormat, scheduledState)
            // This will block the scheduler single thread until the scheduled time (returns false) OR
            // the Future is cancelled due to rescheduling (returns true).
            if (scheduledState != null) {
                if (!awaitWithDeadline(clock, scheduledState.scheduledAt, ourRescheduledFuture)) {
                    log.trace { "Invoking as next $scheduledState" }
                    onTimeReached(scheduledState)
                } else {
                    log.trace { "Rescheduled $scheduledState" }
                }
            } else {
                awaitWithDeadline(clock, clock.instant() + idleWaitSeconds, ourRescheduledFuture)
            }

        }
    }

    private fun rescheduleWakeUp() {
        mutex.alreadyLocked {
            rescheduled?.cancel(false)
        }
    }

    @VisibleForTesting
    internal fun join() {
        mutex.locked {
            running = false
            rescheduleWakeUp()
        }
        schedulerTimerExecutor.join()
    }

    @VisibleForTesting
    internal fun cancelAndWait() {
        schedulerTimerExecutor.shutdownNow()
        schedulerTimerExecutor.join()
    }

    private inner class FlowStartDeduplicationHandler(val scheduledState: ScheduledStateRef, override val flowLogic: FlowLogic<Any?>, override val context: InvocationContext) : DeduplicationHandler, ExternalEvent.ExternalStartFlowEvent<Any?> {
        override val externalCause: ExternalEvent
            get() = this
        override val deduplicationHandler: FlowStartDeduplicationHandler
            get() = this

        override fun insideDatabaseTransaction() {
            schedulerRepo.delete(scheduledState.ref)
        }

        override fun afterDatabaseTransaction() {
            startingStateRefs.remove(scheduledState)
        }

        override fun toString(): String {
            return "${javaClass.simpleName}($scheduledState)"
        }

        override fun wireUpFuture(flowFuture: CordaFuture<FlowStateMachine<Any?>>) {
            _future.captureLater(flowFuture)
            val future = _future.flatMap { it.resultFuture }
            future.then {
                unfinishedSchedules.countDown()
            }
        }

        private val _future = openFuture<FlowStateMachine<Any?>>()
        override val future: CordaFuture<FlowStateMachine<Any?>>
            get() = _future
    }

    private fun onTimeReached(scheduledState: ScheduledStateRef) {
        var flowName: String? = "(unknown)"
        try {
            database.transaction {
                val scheduledFlow = getFlow(scheduledState)
                if (scheduledFlow != null) {
                    flowName = scheduledFlow.javaClass.name
                    // TODO refactor the scheduler to store and propagate the original invocation context
                    val context = InvocationContext.newInstance(InvocationOrigin.Scheduled(scheduledState))
                    val startFlowEvent = FlowStartDeduplicationHandler(scheduledState, scheduledFlow, context)
                    flowStarter.startFlow(startFlowEvent)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to start scheduled flow $flowName for $scheduledState due to an internal error", e)
        }
    }

    private fun getFlow(scheduledState: ScheduledStateRef): FlowLogic<*>? {
        val scheduledActivity = getScheduledActivity(scheduledState)
        var scheduledFlow: FlowLogic<*>? = null
        mutex.locked {
            // need to remove us from those scheduled, but only if we are still next
            if (nextScheduledAction != null && nextScheduledAction === scheduledState) {
                if (scheduledActivity == null) {
                    log.info("Scheduled state $scheduledState has rescheduled to never.")
                    unfinishedSchedules.countDown()
                    schedulerRepo.delete(scheduledState.ref)
                } else if (scheduledActivity.scheduledAt.isAfter(clock.instant())) {
                    log.info("Scheduled state $scheduledState has rescheduled to ${scheduledActivity.scheduledAt}.")
                    val newState = ScheduledStateRef(scheduledState.ref, scheduledActivity.scheduledAt)
                    schedulerRepo.merge(newState)
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
                            //Add this to the in memory list of starting refs so it is not picked up on the next rescheduleWakeUp()
                            startingStateRefs.add(scheduledState)
                            flowLogic
                        }
                    }
                }
            }
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
