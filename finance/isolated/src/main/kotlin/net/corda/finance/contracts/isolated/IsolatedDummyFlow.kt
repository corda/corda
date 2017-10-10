package net.corda.finance.contracts.isolated

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party

/**
 * Just sends a dummy state to the other side: used for testing whether attachments with code in them are being
 * loaded or blocked.
 */
class IsolatedDummyFlow {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(val toWhom: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val tx = AnotherDummyContract().generateInitial(
                    serviceHub.myInfo.legalIdentities.first().ref(0),
                    1234,
                    serviceHub.networkMapCache.notaryIdentities.first()
            )
            val stx = serviceHub.signInitialTransaction(tx)
            subFlow(SendTransactionFlow(initiateFlow(toWhom), stx))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val stx = subFlow(ReceiveTransactionFlow(session, checkSufficientSignatures = false))
            stx.verify(serviceHub)
        }
    }
}
