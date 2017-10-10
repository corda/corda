package net.corda.vega.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.vega.contracts.PortfolioState
import java.time.LocalDate

/**
 * This is the daily flow for generating a revalue of the portfolio initiated by the scheduler as per SIMM
 * requirements
 */
object SimmRevaluation {
    @StartableByRPC
    @SchedulableFlow
    class Initiator(private val curStateRef: StateRef, private val valuationDate: LocalDate) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val stateAndRef = serviceHub.vaultService.queryBy<PortfolioState>(VaultQueryCriteria(stateRefs = listOf(curStateRef))).states.single()
            val curState = stateAndRef.state.data
            if (ourIdentity == curState.participants[0]) {
                val otherParty = serviceHub.identityService.wellKnownPartyFromAnonymous(curState.participants[1])
                require(otherParty != null) { "Other party must be known by this node" }
                subFlow(SimmFlow.Requester(otherParty!!, valuationDate, stateAndRef))
            }
        }
    }
}
