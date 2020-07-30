package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.toMultiMap
import net.corda.core.utilities.seconds
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Read operations that extract information about the flows running in the node.
 */
interface FlowReadOperations {

    fun getWaitingFlows(ids: List<StateMachineRunId>): Set<FlowMetaInfo>

    fun getFlowsCurrentlyWaitingForParties(
        parties: List<Party>,
        onlyIfSuspendedLongerThan: Duration = 0.seconds
    ): Set<FlowMetaInfo>

    fun getFlowsCurrentlyWaitingForPartiesGrouped(
        parties: List<Party>,
        onlyIfSuspendedLongerThan: Duration = 0.seconds
    ): Map<Party, List<FlowMetaInfo>>

    fun getWaitingFlows(): Sequence<FlowStateMachineImpl<*>>
}

/**
 * Write operations that interact with the flows running in the node.
 */
interface FlowWriteOperations

class FlowOperator(private val smm: StateMachineManager, private val clock: Clock) : FlowReadOperations, FlowWriteOperations {

    override fun getWaitingFlows(ids: List<StateMachineRunId>): Set<FlowMetaInfo> {
        // this part is generic between the flow monitor
        return getWaitingFlows()
            .filter { flow -> flow.id in ids }
            .map { flow -> FlowMetaInfo(flow.id, flow.suspendedTimestamp!!, flow.waitingForParties()) }
            .toSet()
    }

    override fun getFlowsCurrentlyWaitingForParties(parties: List<Party>, onlyIfSuspendedLongerThan: Duration): Set<FlowMetaInfo> {
        val now = clock.instant()
        // this part is generic between the flow monitor
        return getWaitingFlows()
            .filter { flow -> flow.isWaitingForParties(parties) }
            .filter { flow -> flow.ongoingDuration(now) >= onlyIfSuspendedLongerThan }
            .map { flow -> FlowMetaInfo(flow.id, flow.suspendedTimestamp!!, flow.waitingForParties()) }
            .toSet()
    }

    override fun getFlowsCurrentlyWaitingForPartiesGrouped(
        parties: List<Party>,
        onlyIfSuspendedLongerThan: Duration
    ): Map<Party, List<FlowMetaInfo>> {
        return getFlowsCurrentlyWaitingForParties(parties, onlyIfSuspendedLongerThan)
            .flatMap { info -> info.waitingForParties.map { it to info } }
            .toMultiMap()
    }

    override fun getWaitingFlows(): Sequence<FlowStateMachineImpl<*>> {
        return smm.snapshot()
            .asSequence()
            .filter { flow -> flow !in smm.flowHospital && flow.isStarted() && flow.isSuspended() }
    }

    private fun FlowStateMachineImpl<*>.isWaitingForParties(parties: List<Party>): Boolean {
        return ioRequest?.let { request ->
            when (request) {
                is FlowIORequest.GetFlowInfo -> request.sessions.any { it.counterparty in parties }
                is FlowIORequest.Receive -> request.sessions.any { it.counterparty in parties }
                is FlowIORequest.Send -> request.sessionToMessage.keys.any { it.counterparty in parties }
                is FlowIORequest.SendAndReceive -> request.sessionToMessage.keys.any { it.counterparty in parties }
                else -> false
            }
        } ?: false
    }

    private fun FlowStateMachineImpl<*>.waitingForParties(): List<Party> {
        return ioRequest?.let { request ->
            when (request) {
                is FlowIORequest.GetFlowInfo -> request.sessions.map { it.counterparty }
                is FlowIORequest.Receive -> request.sessions.map { it.counterparty }
                is FlowIORequest.Send -> request.sessionToMessage.keys.map { it.counterparty }
                is FlowIORequest.SendAndReceive -> request.sessionToMessage.keys.map { it.counterparty }
                else -> emptyList()
            }
        } ?: emptyList()
    }
}

data class FlowMetaInfo(val id: StateMachineRunId, val suspendedTimestamp: Instant, val waitingForParties: List<Party>)

// should find a common place to put these functions
// they have been copied from [FlowMonitor]
// maybe even some of the filtering can be moved to a common place
// maybe the code in general can be merged as flow monitor is so closely related to this class

private val FlowStateMachineImpl<*>.ioRequest get() = (snapshot().checkpoint.flowState as? FlowState.Started)?.flowIORequest

private fun FlowStateMachineImpl<*>.ongoingDuration(now: Instant): Duration {
    return suspendedTimestamp?.let { Duration.between(it, now) } ?: Duration.ZERO
}

private val FlowStateMachineImpl<*>.suspendedTimestamp get() = transientState?.value?.checkpoint?.timestamp

private fun FlowStateMachineImpl<*>.isSuspended() = !snapshot().isFlowResumed

private fun FlowStateMachineImpl<*>.isStarted() = transientState?.value?.checkpoint?.flowState is FlowState.Started

private operator fun StaffedFlowHospital.contains(flow: FlowStateMachine<*>) = contains(flow.id)