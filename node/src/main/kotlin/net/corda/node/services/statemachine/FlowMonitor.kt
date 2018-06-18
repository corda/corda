package net.corda.node.services.statemachine

import net.corda.core.flows.FlowSession
import net.corda.core.internal.FlowIORequest
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.LifecycleSupport
import java.time.Duration
import java.time.Instant
import java.time.Instant.now
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class FlowMonitor private constructor(private val retrieveFlows: () -> Set<FlowStateMachineImpl<*>>,
                                               private val monitoringPeriod: Duration,
                                               private val suspensionLoggingThreshold: Duration,
                                               private val scheduler: ScheduledExecutorService,
                                               private val shutdownScheduler: Boolean) : LifecycleSupport {

    internal constructor(retrieveFlows: () -> Set<FlowStateMachineImpl<*>>, monitoringPeriod: Duration, suspensionLoggingThreshold: Duration, scheduler: ScheduledExecutorService) : this(retrieveFlows, monitoringPeriod, suspensionLoggingThreshold, scheduler, false)

    internal constructor(retrieveFlows: () -> Set<FlowStateMachineImpl<*>>, monitoringPeriod: Duration, suspensionLoggingThreshold: Duration) : this(retrieveFlows, monitoringPeriod, suspensionLoggingThreshold, defaultScheduler(), true)

    private companion object {
        private fun defaultScheduler() = Executors.newSingleThreadScheduledExecutor()

        private val logger = loggerFor<FlowMonitor>()
    }

    override var started = false

    override fun start() {
        synchronized(started) {
            scheduler.scheduleAtFixedRate({ logFlowsWaitingForParty(suspensionLoggingThreshold) }, 0, monitoringPeriod.toMillis(), TimeUnit.MILLISECONDS)
            started = true
        }
    }

    override fun stop() {
        synchronized(started) {
            if (shutdownScheduler) {
                scheduler.shutdown()
            }
            started = false
        }
    }

    private fun logFlowsWaitingForParty(suspensionLoggingThreshold: Duration) {
        val now = now()
        val flows = retrieveFlows()
        for (flow in flows) {
            if (flow.isStarted() && flow.ongoingDuration(now) >= suspensionLoggingThreshold) {
                flow.ioRequest()?.let { request -> warningMessageForFlowWaitingOnIo(request, flow, now) }?.let(logger::info)
            }
        }
    }

    private fun warningMessageForFlowWaitingOnIo(request: FlowIORequest<*>, flow: FlowStateMachineImpl<*>, now: Instant): String {
        val message = StringBuilder("Flow with id ${flow.id.uuid} has been waiting for ${flow.ongoingDuration(now).toMillis() / 1000} seconds ")
        message.append(
                when (request) {
                    is FlowIORequest.Send -> "to send a message to parties ${request.sessionToMessage.keys.partiesInvolved()}"
                    is FlowIORequest.Receive -> "to receive messages from parties ${request.sessions.partiesInvolved()}"
                    is FlowIORequest.SendAndReceive -> "to send and receive messages from parties ${request.sessionToMessage.keys.partiesInvolved()}"
                    is FlowIORequest.WaitForLedgerCommit -> "for the ledger to commit transaction with hash ${request.hash}"
                    is FlowIORequest.GetFlowInfo -> "to get flow information from parties ${request.sessions.partiesInvolved()}"
                    is FlowIORequest.Sleep -> "to wake up from sleep ending at ${LocalDateTime.ofInstant(request.wakeUpAfter, ZoneId.systemDefault())}"
                    FlowIORequest.WaitForSessionConfirmations -> "for sessions to be confirmed"
                    is FlowIORequest.ExecuteAsyncOperation -> "for an asynchronous operation to complete"
                }
        )
        message.append(".")
        return message.toString()
    }

    private fun Iterable<FlowSession>.partiesInvolved() = map { it.counterparty }.joinToString(", ", "[", "]")

    private fun FlowStateMachineImpl<*>.ioRequest() = (transientState?.value?.checkpoint?.flowState as? FlowState.Started)?.flowIORequest

    private fun FlowStateMachineImpl<*>.ongoingDuration(now: Instant) = Duration.between(createdAt(), now)

    private fun FlowStateMachineImpl<*>.createdAt() = context.trace.invocationId.timestamp

    private fun FlowStateMachineImpl<*>.isStarted() = transientState?.value?.checkpoint?.flowState is FlowState.Started
}