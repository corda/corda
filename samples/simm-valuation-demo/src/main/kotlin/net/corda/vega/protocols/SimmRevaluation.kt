package net.corda.vega.protocols

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.protocols.ProtocolLogic
import net.corda.vega.contracts.PortfolioState
import java.time.LocalDate

/**
 * This is the daily protocol for generating a revalue of the portfolio initiated by the scheduler as per SIMM
 * requirements
 */
object SimmRevaluation {
    class Initiator(val curStateRef: StateRef, val valuationDate: LocalDate) : ProtocolLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {
            val stateAndRef = serviceHub.vaultService.linearHeadsOfType<PortfolioState>().values.first { it.ref == curStateRef }
            val curState = stateAndRef.state.data
            val myIdentity = serviceHub.myInfo.legalIdentity
            if (myIdentity.name == curState.parties[0].name) {
                val otherParty = curState.parties[1]
                subProtocol(SimmProtocol.Requester(otherParty, valuationDate, stateAndRef))
            }
        }
    }
}
