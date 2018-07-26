package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * Given a Linear StateAndRef, this flow looks up which parties maintain the state object and then requests an
 * update from them.
 */
class ReferenceDataUpdateRequest {

    @CordaSerializable
    enum class Response {
        SUCCESS,
        FAILURE
    }

    /**
     * Notaries will reject transactions containing stale reference states. To notarise such transactions, the co-ordinating
     * party must update their reference data. If the owner (or maintainer) of some reference data is identified in
     * the reference state itself, then the party using the reference state can request an update from the owner.
     */
    @InitiatingFlow
    class Initiator(val oldStateAndRef: StateAndRef<LinearState>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val state = oldStateAndRef.state.data
            val linearId = state.linearId

            // Check if there are any parties to request updates from.
            val addressableMaintainers: List<Party> = state.participants.map { it as Party }
            check(addressableMaintainers.isNotEmpty()) { "There's no-one to request an update from!" }

            // Try getting the update from one of the maintainers.
            addressableMaintainers.forEach { party ->
                val session = initiateFlow(party)
                val response = session.sendAndReceive<Response>(linearId).unwrap { it }
                when (response) {
                    Response.SUCCESS -> {
                        session.send(linearId)
                        logger.info("Receiving new transactions...")
                        subFlow(ReceiveTransactionFlow(
                                otherSideSession = session,
                                statesToRecord = StatesToRecord.ALL_VISIBLE
                        ))
                        return
                    }
                    Response.FAILURE -> logger.info("$party could not provide updates to $linearId.")
                }
            }

            logger.info("No-one could provide an update for $linearId.")
        }
    }

    @InitiatedBy(Initiator::class)
    class Handler(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive a reference data update request.
            val linearId = otherSession.receive<UniqueIdentifier>().unwrap { it }

            // Find the transaction which created the newest version of the linear state.
            logger.info("Received request for reference data update. Querying for linearID $linearId...")
            val query = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(linearId.id))
            val result = serviceHub.vaultService.queryBy<LinearState>(query).states.singleOrNull()

            // TODO: Check to see if they already have the latest version.

            if (result == null) {
                // We don't have the transaction!
                otherSession.send(Response.FAILURE)
            } else {
                otherSession.send(Response.SUCCESS)
                // We _must_ have the transaction which created the latest version of the state.
                val stx = serviceHub.validatedTransactions.getTransaction(result.ref.txhash) as SignedTransaction
                // Send on the transaction and dependencies.
                logger.info("Sending transaction and dependencies for ${stx.id}...")
                subFlow(SendTransactionFlow(otherSession, stx))
            }
        }
    }

}