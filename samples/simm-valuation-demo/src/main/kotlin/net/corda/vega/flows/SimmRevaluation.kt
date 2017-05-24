package net.corda.vega.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.linearHeadsOfType
import net.corda.vega.contracts.PortfolioState
import java.time.LocalDate

/**
 * This is the daily flow for generating a revalue of the portfolio initiated by the scheduler as per SIMM
 * requirements
 */
object SimmRevaluation {
    @StartableByRPC
    @SchedulableFlow
    class Initiator(val curStateRef: StateRef, val valuationDate: LocalDate) : FlowLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {
            val stateAndRef = serviceHub.vaultService.linearHeadsOfType<PortfolioState>().values.first { it.ref == curStateRef }
            val curState = stateAndRef.state.data
            val myIdentity = serviceHub.myInfo.legalIdentity
            if (myIdentity == curState.parties[0]) {
                val otherParty = serviceHub.identityService.partyFromAnonymous(curState.parties[1])
                require(otherParty != null) { "Other party must be known by this node" }
                subFlow(SimmFlow.Requester(otherParty!!, valuationDate, stateAndRef))
            }
        }
    }
}
