package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.utilities.unwrap

class NotaryQueryClientFlow {

    @StartableByRPC
    class DoubleSpendAudit(private val stateRef: StateRef) :
        FlowLogic<NotaryQuery.Result.SpentStates>() {

        @Suspendable
        override fun call(): NotaryQuery.Result.SpentStates {
            return subFlow(InitiateQuery(NotaryQuery.Request.SpentStates(stateRef)))
                    as NotaryQuery.Result.SpentStates
        }
    }

    /**
     * Trivial flow to send a generic notary query request, and to return the response.
     *
     * Note that it is expected that the receiving flow is only run on the Notary (i.e.
     * both the initiating flow and this are running on the same system), so we do no
     * checking of the identity of the responder or the integrity of the message. This will
     * need to be revisited if this flow is initiated by a Corda node in future.
     */
    @InitiatingFlow
    class InitiateQuery(private val request: NotaryQuery.Request) :
            FlowLogic<NotaryQuery.Result>() {

        @Suspendable
        override fun call() : NotaryQuery.Result {
            val notarySession = initiateFlow(serviceHub.myInfo.legalIdentities.first())
            notarySession.send(request, maySkipCheckpoint = true)
            return notarySession.receive(NotaryQuery.Result::class.java, maySkipCheckpoint = true)
                    .unwrap { data -> data }
        }
    }
}
