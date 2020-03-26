package net.corda.core.internal.notary

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.NotaryQuery
import net.corda.core.flows.NotaryQueryClientFlow
import net.corda.core.utilities.unwrap

/**
 * Trivial flow to handle receipt of a new notary query request, which is delegated to
 * the notary service and sends the result back to the client.
 *
 * Note that it is expected that the initiating flow is only run on the Notary (i.e.
 * both the initiating flow and this are running on the same system), so we do no
 * checking of the identity of the sender or the integrity of the message. This will
 * need to be revisited if it becomes possible for a Corda node to initiate this flow
 * in future.
 */
@InitiatedBy(NotaryQueryClientFlow.InitiateQuery::class)
class NotaryQueryFlow(private val otherSideSession: FlowSession,
                      private val service: SinglePartyNotaryService) : FlowLogic<Void?>() {

    @Suspendable
    override fun call(): Void? {
        val requestPayload = otherSideSession.receive(
                NotaryQuery.Request::class.java, maySkipCheckpoint = true).unwrap { it }

        val result = try {
            service.processQuery(requestPayload)
        } catch (e: UnsupportedOperationException) {
            throw FlowException(e)
        }

        otherSideSession.send(result, maySkipCheckpoint = true)
        return null
    }
}
