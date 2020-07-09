package net.corda.node.services.statemachine

import net.corda.core.internal.FlowIORequest
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class FlowSleepScheduler(private val innerState: StateMachineInnerState, private val scheduledExecutor: ScheduledExecutorService) {

    private companion object {
        val log = contextLogger()
    }

    /**
     * Put a flow to sleep for a specified duration.
     *
     * @param fiber The [FlowFiber] that will be woken up after sleeping
     * @param currentState The current [StateMachineState]
     * @param duration How long to sleep for
     */
    fun sleep(fiber: FlowFiber, currentState: StateMachineState, duration: Duration) {
        // No other future should be running, cancel it if there is
        currentState.future?.run {
            log.debug { "Cancelling the existing future for flow ${fiber.id}" }
            cancelIfRunning()
        }
        currentState.future = setAlarmClock(fiber, duration)
    }

    private fun Future<*>.cancelIfRunning() {
        if (!isDone) cancel(true)
    }

    private fun setAlarmClock(fiber: FlowFiber, duration: Duration): ScheduledFuture<Unit> {
        val instance = fiber.instanceId
        log.debug { "Putting flow ${instance.runId} to sleep for $duration" }
        return scheduledExecutor.schedule<Unit>(
            {
                log.debug { "Scheduling flow wake up event for flow ${instance.runId}" }
                scheduleWakeUp(instance)
            },
            duration.toMillis(), TimeUnit.MILLISECONDS
        )
    }

    private fun scheduleWakeUp(instance: StateMachineInstanceId) {
        innerState.withLock {
            flows[instance.runId]?.let { flow ->
                // Only schedule a wake up event if the fiber the flow is executing on has not changed
                if (flow.fiber.instanceId == instance) {
                    flow.fiber.scheduleEvent(Event.WakeUpFromSleep)
                }
            }
        }
    }
}