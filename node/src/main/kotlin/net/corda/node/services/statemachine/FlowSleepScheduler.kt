package net.corda.node.services.statemachine

import net.corda.core.internal.FlowIORequest
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class FlowSleepScheduler(private val smm: StateMachineManagerInternal, private val scheduledExecutor: ScheduledExecutorService) {

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

    /**
     * Schedule a wake up event.
     *
     * @param fiber The [FlowFiber] to schedule a wake up event for
     */
    fun scheduleWakeUp(fiber: FlowFiber) {
        fiber.scheduleEvent(Event.WakeUpFromSleep)
    }

    /**
     * Cancel a sleeping flow's future. Note, this does not cause the flow to wake up.
     *
     * @param currentState The current [StateMachineState]
     */
    fun cancel(currentState: StateMachineState) {
        (currentState.checkpoint.flowState as? FlowState.Started)?.let { flowState ->
            if (currentState.isWaitingForFuture && flowState.flowIORequest is FlowIORequest.Sleep) {
                (currentState.future as? ScheduledFuture)?.run {
                    log.debug { "Cancelling the sleep scheduled future for flow ${currentState.flowLogic.runId}" }
                    cancelIfRunning()
                    currentState.future = null
                }
            }

        }
    }

    private fun Future<*>.cancelIfRunning() {
        if (!isDone) cancel(true)
    }

    private fun setAlarmClock(fiber: FlowFiber, duration: Duration): ScheduledFuture<Unit> {
        val instance = fiber.instanceId
        log.debug { "Putting flow to sleep for $duration" }
        return scheduledExecutor.schedule<Unit>(
            {
                log.debug { "Scheduling flow wake up event for flow ${instance.runId}" }
                // This passes back into the SMM to check that the fiber that went to sleep is the same fiber that is now being scheduled
                // with the wake up event
                smm.scheduleFlowWakeUp(instance)
            },
            duration.toMillis(), TimeUnit.MILLISECONDS
        )
    }
}