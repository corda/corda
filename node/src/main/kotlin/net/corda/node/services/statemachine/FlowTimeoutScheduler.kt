package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.ServiceHubInternal
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class FlowTimeoutScheduler(
    private val innerState: StateMachineInnerState,
    private val scheduledExecutor: ScheduledExecutorService,
    private val serviceHub: ServiceHubInternal
) {

    private companion object {
        val log = contextLogger()
    }

    /**
     * Schedules the flow [flowId] to be retried if it does not finish within the timeout period
     * specified in the config.
     *
     * @param flowId The id of the flow that the timeout is scheduled for
     */
    fun timeout(flowId: StateMachineRunId) {
        timeout(flowId) { flow, retryCount ->
            val defaultTimeout = calculateDefaultTimeoutSeconds(retryCount)
            val scheduledFuture = scheduleTimeoutException(flow, defaultTimeout)
            log.debug("Setting default time-out on timed flow $flowId to $defaultTimeout seconds (retry #$retryCount).")
            ScheduledTimeout(scheduledFuture, retryCount + 1)
        }
    }

    /**
     * Cancel a flow's timeout future.
     *
     * @param flowId The flow's id
     */
    fun cancel(flowId: StateMachineRunId) {
        innerState.withLock {
            timedFlows[flowId]?.let { (future, _) ->
                future.cancelIfRunning()
                timedFlows.remove(flowId)
            }
        }
    }

    /**
     * Resets a flow's timeout with the input timeout duration, only if it is longer than the default flow timeout configuration.
     *
     * @param flowId The flow's id
     * @param timeoutSeconds The custom timeout
     */
    fun resetCustomTimeout(flowId: StateMachineRunId, timeoutSeconds: Long) {
        if (timeoutSeconds < serviceHub.configuration.flowTimeout.timeout.seconds) {
            log.debug { "Ignoring request to set time-out on timed flow $flowId to $timeoutSeconds seconds which is shorter than default of ${serviceHub.configuration.flowTimeout.timeout.seconds} seconds." }
            return
        }
        log.debug { "Processing request to set time-out on timed flow $flowId to $timeoutSeconds seconds." }
        timeout(flowId) { flow, retryCount ->
            val scheduledFuture = scheduleTimeoutException(flow, timeoutSeconds)
            ScheduledTimeout(scheduledFuture, retryCount)
        }
    }

    private inline fun timeout(flowId: StateMachineRunId, timeout: (flow: Flow<*>, retryCount: Int) -> ScheduledTimeout) {
        innerState.withLock {
            val flow = flows[flowId]
            if (flow != null) {
                val retryCount = timedFlows[flowId]?.let { (future, retryCount) ->
                    future.cancelIfRunning()
                    retryCount
                } ?: 0
                timedFlows[flowId] = timeout(flow, retryCount)
            } else {
                log.warn("Unable to schedule timeout for flow $flowId – flow not found.")
            }
        }
    }

    /** Schedules a [FlowTimeoutException] to be fired in order to restart the flow. */
    private fun scheduleTimeoutException(flow: Flow<*>, delay: Long): ScheduledFuture<*> {
        return scheduledExecutor.schedule({
            val event = Event.Error(FlowTimeoutException())
            flow.fiber.scheduleEvent(event)
        }, delay, TimeUnit.SECONDS)
    }

    private fun calculateDefaultTimeoutSeconds(retryCount: Int): Long {
        return serviceHub.configuration.flowTimeout.run {
            val timeoutDelaySeconds = (timeout.seconds *
                    Math.pow(backoffBase, Integer.min(retryCount, maxRestartCount).toDouble())).toLong()

            // Introduce a variable delay to ensure that if a large spike of transactions are
            // received, we do not trigger retries of them all at the same time. This results
            // in an effective timeout between 100-150% of the calculated timeout.
            maxOf(1L, (timeoutDelaySeconds * (1 + (Math.random() * 0.5))).toLong())
        }
    }

    private fun Future<*>.cancelIfRunning() {
        if (!isDone) cancel(true)
    }
}