package net.corda.node.services.statemachine

import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import java.time.Duration

/**
an interface to provide a simple interaction with the Statemachine with regards to
filtering and enriching flow checkpoints with information about the counterparties
they are involved with
 */
interface CheckpointSessionInfoProvider {

    /**
     *  Discover what flows are currently waiting for a given counterparty
     *  multiple flows can wait for a given party and any give flow can be waiting for multiple parties
     *  @param parties - parties to inspect statemachine for
     *  @param onlyIfSuspendedLongerThan - only if the flow has been suspended for longer than this duration should it be returned
     *  @return map of requested party, to list of all flows they are currently responsible for blocking
     */
    fun getStateMachinesCurrentlyWaitingFor(parties: List<Party>, onlyIfSuspendedLongerThan: Duration = Duration.ZERO): Map<Party, List<StateMachineRunId>>

    /**
     * Discover what parties a given flow is blocked on
     * @param flowIds - list of flows to discover counterparty information for
     * @return map of requested flow to list of all parties it is currently waiting for
     */
    fun getPartiesStateMachineCurrentlyWaitingFor(flowIds: List<StateMachineRunId>): Map<StateMachineRunId, List<Party>>
}