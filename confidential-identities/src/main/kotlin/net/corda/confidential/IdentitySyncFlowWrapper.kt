package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.unwrap

/**
 * A simple wrapper of IdentitySyncFlow to make it standalone with its own flow session.
 * It turns out to be the same as the wrapper in IdentitySyncFlowTests.
 */
object IdentitySyncFlowWrapper {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(val otherParty: Party,
                    val tx: WireTransaction) : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val otherSideSession = initiateFlow(otherParty)
            subFlow(IdentitySyncFlow.Send(otherSideSession, tx))
            return otherSideSession.receive<Boolean>().unwrap { it }
        }
    }

    @InitiatedBy(Initiator::class)
    class Receive(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(IdentitySyncFlow.Receive(otherSideSession))
            otherSideSession.send(true)
        }
    }
}