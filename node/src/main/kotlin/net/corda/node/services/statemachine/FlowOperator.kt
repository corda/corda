package net.corda.node.services.statemachine

import net.corda.core.flows.FlowSession
import net.corda.core.flows.StateMachineRunId
import net.corda.core.flows.externalOperationImplName
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.FlowStateMachine
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.seconds
import java.time.Clock
import java.time.Duration
import java.time.Instant

private operator fun StaffedFlowHospital.contains(flow: FlowStateMachine<*>) = contains(flow.id)

private fun Set<FlowSession>.contains(parties: List<Party>) = this.any { it.counterparty in parties }

private fun Map<FlowSession, *>.contains(parties: List<Party>) = keys.any { it.counterparty in parties }

private fun FlowStateMachineImpl<*>.flowInfoOf(
        source: WaitingSource,
        waitingForParties: List<WaitingPartyInfo>,
        externalOperationName: String? = null
): WaitingFlowInfo =
        WaitingFlowInfo(
                id,
                suspendedTimestamp(),
                source,
                waitingForParties,
                externalOperationName)

/**
 * Stage in which the flow is suspended
 */
enum class WaitingSource {
    SEND,
    RECEIVE,
    SEND_AND_RECEIVE,
    CLOSE_SESSIONS,
    WAIT_FOR_LEDGER_COMMIT,
    GET_FLOW_INFO,
    SLEEP,
    WAIT_FOR_SESSIONS_CONFIRMATIONS,
    EXTERNAL_OPERATION
}

/**
 * Waiting party information and the payload for operations
 *  - send
 *  - send and receive
 */
data class WaitingPartyInfo(
        val payload: SerializedBytes<Any>?,
        val party: Party
)

/**
 * Information about a flow which is waiting to be resumed
 * The flow is considered to be waiting if:
 *  - It's started.
 *  - Is not admitted to the hospital
 *  - And it's in the suspended state for an IO request.
 */
data class WaitingFlowInfo(
        val id: StateMachineRunId,
        val suspendedTimestamp: Instant,
        val source: WaitingSource,
        val waitingForParties: List<WaitingPartyInfo>,
        val externalOperationImplName: String? = null
)

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

/**
 * Implements flow operators
 * @see FlowReadOperations
 * @see FlowWriteOperations
 */
class FlowOperator(private val smm: StateMachineManager, private val clock: Clock) : FlowReadOperations, FlowWriteOperations {

    override fun getWaitingFlows(ids: List<StateMachineRunId>): Set<WaitingFlowInfo> {
        val result = LinkedHashSet<WaitingFlowInfo>()
        getWaitingFlows()
                .filter { flow -> flow.id in ids }
                .forEach { flow ->
                    val waitingParties = flow.waitingForParties(listOf())
                    if (waitingParties != null) {
                        result.add(waitingParties)
                    }
                }
        return result
    }

    override fun getFlowsCurrentlyWaitingForParties(parties: List<Party>, onlyIfSuspendedLongerThan: Duration): Set<WaitingFlowInfo> {
        val now = clock.instant()
        val result = LinkedHashSet<WaitingFlowInfo>()
        getWaitingFlows()
                .filter { flow -> flow.ongoingDuration(now) >= onlyIfSuspendedLongerThan }
                .forEach { flow ->
                    val waitingParties = flow.waitingForParties(parties)
                    if (waitingParties != null) {
                        result.add(waitingParties)
                    }
                }
        return result
    }

    override fun getFlowsCurrentlyWaitingForPartiesGrouped(
            parties: List<Party>,
            onlyIfSuspendedLongerThan: Duration
    ): Map<Party, List<WaitingFlowInfo>> {
        return getFlowsCurrentlyWaitingForParties(parties, onlyIfSuspendedLongerThan)
                .flatMap { info -> info.waitingForParties.map { it to info } }
                .groupBy({ it.first.party }) { it.second }
    }

    override fun getWaitingFlows(): Sequence<FlowStateMachineImpl<*>> {
        return smm.snapshot()
                .asSequence()
                .filter { flow -> flow !in smm.flowHospital && flow.isStarted() && flow.isSuspended() }
    }

    @Suppress("ComplexMethod")
    private fun FlowStateMachineImpl<*>.waitingForParties(parties: List<Party>): WaitingFlowInfo? {
        return ioRequest()?.let { request ->
            when (request) {
                is FlowIORequest.Send -> sendInfo(parties, request)
                is FlowIORequest.Receive -> receiveInfo(parties, request)
                is FlowIORequest.SendAndReceive -> sendAndReceiveInfo(parties, request)
                is FlowIORequest.CloseSessions -> closeSessionsInfo(parties, request)
                is FlowIORequest.WaitForLedgerCommit -> waitForLedgerCommitInfo(parties)
                is FlowIORequest.GetFlowInfo -> getFlowInfoInfo(parties, request)
                is FlowIORequest.Sleep -> sleepInfo(parties)
                is FlowIORequest.WaitForSessionConfirmations -> waitForSessionConfirmationsInfo(parties)
                is FlowIORequest.ExecuteAsyncOperation -> executeAsyncOperationInfo(parties, request)
                else -> null
            }
        }
    }

    private fun FlowStateMachineImpl<*>.executeAsyncOperationInfo(parties: List<Party>, request: FlowIORequest.ExecuteAsyncOperation<*>): WaitingFlowInfo? {
        return if (parties.isEmpty()) {
            flowInfoOf(
                    WaitingSource.EXTERNAL_OPERATION,
                    listOf(),
                    request.operation.externalOperationImplName
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.waitForSessionConfirmationsInfo(parties: List<Party>): WaitingFlowInfo? {
        return if (parties.isEmpty()) {
            flowInfoOf(
                    WaitingSource.WAIT_FOR_SESSIONS_CONFIRMATIONS,
                    listOf()
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.sleepInfo(parties: List<Party>): WaitingFlowInfo? {
        return if (parties.isEmpty()) {
            flowInfoOf(
                    WaitingSource.SLEEP,
                    listOf()
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.getFlowInfoInfo(parties: List<Party>, request: FlowIORequest.GetFlowInfo): WaitingFlowInfo? {
        return if (parties.isEmpty() || request.sessions.contains(parties)) {
            flowInfoOf(
                    WaitingSource.GET_FLOW_INFO,
                    request.sessions.map { WaitingPartyInfo(null, it.counterparty) }
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.waitForLedgerCommitInfo(parties: List<Party>): WaitingFlowInfo? {
        return if (parties.isEmpty()) {
            flowInfoOf(
                    WaitingSource.WAIT_FOR_LEDGER_COMMIT,
                    listOf()
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.closeSessionsInfo(parties: List<Party>, request: FlowIORequest.CloseSessions): WaitingFlowInfo? {
        return if (parties.isEmpty() || request.sessions.contains(parties)) {
            flowInfoOf(
                    WaitingSource.CLOSE_SESSIONS,
                    request.sessions.map { WaitingPartyInfo(null, it.counterparty) }
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.sendAndReceiveInfo(parties: List<Party>, request: FlowIORequest.SendAndReceive): WaitingFlowInfo? {
        return if (parties.isEmpty() || request.sessionToMessage.contains(parties)) {
            flowInfoOf(
                    WaitingSource.SEND_AND_RECEIVE,
                    request.sessionToMessage.map { WaitingPartyInfo(it.value, it.key.counterparty) }
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.receiveInfo(parties: List<Party>, request: FlowIORequest.Receive): WaitingFlowInfo? {
        return if (parties.isEmpty() || request.sessions.contains(parties)) {
            flowInfoOf(
                    WaitingSource.RECEIVE,
                    request.sessions.map { WaitingPartyInfo(null, it.counterparty) }
            )
        } else {
            null
        }
    }

    private fun FlowStateMachineImpl<*>.sendInfo(parties: List<Party>, request: FlowIORequest.Send): WaitingFlowInfo? {
        return if (parties.isEmpty() || request.sessionToMessage.contains(parties)) {
            flowInfoOf(
                    WaitingSource.SEND,
                    request.sessionToMessage.map { WaitingPartyInfo(it.value, it.key.counterparty) }
            )
        } else {
            null
        }
    }
}
