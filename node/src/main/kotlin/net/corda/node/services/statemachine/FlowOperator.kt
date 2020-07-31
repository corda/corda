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
 * Basic information about a flow which is waiting to be resumed
 * The flow is considered to be waiting if:
 *  - It's started.
 *  - And it's in the suspended state for one of IO requests:
 *  -- FlowIORequest.Receive.
 *  -- FlowIORequest.Send.
 *  -- FlowIORequest.SendAndReceive.
 *  -- FlowIORequest.GetFlowInfo.
 */
data class WaitingFlowInfo(val id: StateMachineRunId, val suspendedTimestamp: Instant, val waitingForParties: List<Party>)

/**
 * Read operations that extract information about the flows running in the node.
 */
interface FlowReadOperations {

    /**
     * Returns waiting flows for a specified list of state machine run ids.
     * The flow is considered to be waiting if:
     *  - It's started.
     *  - And it's in the suspended state.
     */
    fun getWaitingFlows(ids: List<StateMachineRunId>): Set<WaitingFlowInfo>

    /**
     * Returns waiting flows for a specified list of other parties it's waiting for.
     * @see WaitingFlowInfo
     */
    fun getFlowsCurrentlyWaitingForParties(
        parties: List<Party>,
        onlyIfSuspendedLongerThan: Duration = 0.seconds
    ): Set<WaitingFlowInfo>

    /**
     * Returns waiting flows for a specified list of other parties it's waiting for grouped by the party.
     * @see WaitingFlowInfo
     */
    fun getFlowsCurrentlyWaitingForPartiesGrouped(
        parties: List<Party>,
        onlyIfSuspendedLongerThan: Duration = 0.seconds
    ): Map<Party, List<WaitingFlowInfo>>

    /**
     * Returns all waiting flow state machines.
     * The flow is considered to be waiting if:
     *  - It's started.
     *  - And it's in the suspended state.
     */
    fun getWaitingFlows(): Sequence<FlowStateMachineImpl<*>>
}

/**
 * Write operations that interact with the flows running in the node.
 */
interface FlowWriteOperations

class FlowOperator(private val smm: StateMachineManager, private val clock: Clock) : FlowReadOperations, FlowWriteOperations {

    override fun getWaitingFlows(ids: List<StateMachineRunId>): Set<WaitingFlowInfo> {
        return getWaitingFlows()
            .filter { flow -> flow.id in ids }
            .map { flow -> WaitingFlowInfo(flow.id, flow.suspendedTimestamp(), flow.waitingForParties()) }
            .toSet()
    }

    override fun getFlowsCurrentlyWaitingForParties(parties: List<Party>, onlyIfSuspendedLongerThan: Duration): Set<WaitingFlowInfo> {
        val now = clock.instant()
        return getWaitingFlows()
            .filter { flow -> flow.isWaitingForParties(parties) }
            .filter { flow -> flow.ongoingDuration(now) >= onlyIfSuspendedLongerThan }
            .map { flow -> WaitingFlowInfo(flow.id, flow.suspendedTimestamp(), flow.waitingForParties()) }
            .toSet()
    }

    override fun getFlowsCurrentlyWaitingForPartiesGrouped(
        parties: List<Party>,
        onlyIfSuspendedLongerThan: Duration
    ): Map<Party, List<WaitingFlowInfo>> {
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
        return ioRequest()?.let { request ->
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
        return ioRequest()?.let { request ->
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

private operator fun StaffedFlowHospital.contains(flow: FlowStateMachine<*>) = contains(flow.id)