package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class FlowSleepScheduler(private val scheduledExecutor: ScheduledExecutorService) {

    private companion object {
        val log = contextLogger()
    }

    /**
     * Put a flow to sleep for a specified duration.
     *
     * @param innerState The [InnerState] used inside [StateMachineManager]
     * @param id The id of the flow to put to sleep
     * @param duration How long to sleep for
     */
    fun sleep(innerState: InnerState, id: StateMachineRunId, duration: Duration) {
        val flow = innerState.flows[id]
        if (flow != null) {
            // If the flow already has a future scheduled, cancel it and schedule a new future
            innerState.sleepingFlows.compute(id) { _, future ->
                future?.run {
                    log.debug { "Cancelling the existing sleep scheduled future for flow ${flow.fiber.id}" }
                    cancelIfRunning()
                }
                setAlarmClock(innerState, flow, duration)
            }
        } else {
            log.warn("Unable to schedule sleep for flow $id – flow not found.")
        }
    }

    /**
     * Cancel a sleeping flow's future. Note, this does not cause the flow to wake up.
     *
     * @param innerState The [InnerState] used inside [StateMachineManager]
     * @param id The id of the flow whose future must be cancelled
     */
    fun cancel(innerState: InnerState, id: StateMachineRunId) {
        innerState.sleepingFlows.remove(id)?.run {
            log.debug { "Cancelling the sleep scheduled future for flow $id" }
            cancelIfRunning()
        }
    }

    private fun ScheduledFuture<Unit>.cancelIfRunning() {
        if (!isDone) cancel(true)
    }

    private fun setAlarmClock(innerState: InnerState, flow: Flow, duration: Duration): ScheduledFuture<Unit> {
        log.debug { "Putting flow to sleep for $duration" }
        return scheduledExecutor.schedule<Unit>(
            {
                log.debug { "Scheduling flow wake up event for flow ${flow.fiber.id}" }
                innerState.sleepingFlows.remove(flow.fiber.id)
                flow.fiber.scheduleEvent(Event.WakeUpFromSleep)
            },
            duration.toMillis(), TimeUnit.MILLISECONDS
        )
    }
}