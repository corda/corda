package net.corda.notaryhealthcheck.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.notary.generateSignature
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.UntrustworthyData
import net.corda.notaryhealthcheck.contract.NullContract
import net.corda.notaryhealthcheck.utils.Monitorable
import java.util.function.Predicate

@StartableByService
class HealthCheckFlow(monitorable: Monitorable) : FlowLogic<List<TransactionSignature>>() {
    private val notary = monitorable.notary
    private val party = monitorable.party

    data class NullCommand(val data: Byte = 0) : CommandData // Param must be public for AMQP serialization.
    data class State(override val participants: List<AbstractParty>) : ContractState

    @Suspendable
    override fun call(): List<TransactionSignature> {
        val stx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            addOutputState(State(listOf(ourIdentity)), NullContract::class.java.name, AlwaysAcceptAttachmentConstraint)
            addCommand(NullCommand(), listOf(ourIdentity.owningKey))
        })
        return subFlow(NotaryClientFlow(stx, party, notary))
    }

    /**
     * This class needs to reimplement a fair bit of the NotaryFlow.Client() flow: As we want to test cluster members
     * of clustered notaries separately, we need to send requests to these. However, the look-up whether they're validating
     * has to be done on the notary service, not the cluster member - therefore we cannot use the notaris() method
     * and have to reimplement all private methods called by this.
     * The class still has to be derived from NotaryFlow.Client so the counter flow gets registered correctly.
     * And we can't use any fancy reflection tricks as they don't play ball with @Suspendable.
     *
     * @param stx The transaction to notaries
     * @param party The party identifying the notary or notary cluster node that we want to send the work to in order to check it
     * @param notaryParty The party indentifying the notary service for the given party
     */
    class NotaryClientFlow(private val stx: SignedTransaction, private val party: Party, private val notaryParty: Party) : NotaryFlow.Client(stx) {

        @Suspendable
        @Throws(NotaryException::class)
        override fun call(): List<TransactionSignature> {
            val session = initiateFlow(party)
            val requestSignature = NotarisationRequest(stx.inputs, stx.id).generateSignature(serviceHub)
            return validateResponse(if (serviceHub.networkMapCache.isValidatingNotary(notaryParty)) {
                sendAndReceiveValidating(session, requestSignature)
            } else {
                sendAndReceiveNonValidating(notaryParty, session, requestSignature)
            }, notaryParty)
        }


        @Suspendable
        private fun sendAndReceiveValidating(session: FlowSession, signature: NotarisationRequestSignature): UntrustworthyData<NotarisationResponse> {
            val payload = NotarisationPayload(stx, signature)
            subFlow(NotarySendTransactionFlow(session, payload))
            return session.receive()
        }

        @Suspendable
        private fun sendAndReceiveNonValidating(notaryParty: Party, session: FlowSession, signature: NotarisationRequestSignature): UntrustworthyData<NotarisationResponse> {
            val ctx = stx.coreTransaction
            val tx = when (ctx) {
                is ContractUpgradeWireTransaction -> ctx.buildFilteredTransaction()
                is WireTransaction -> ctx.buildFilteredTransaction(Predicate { it is StateRef || it is TimeWindow || it == notaryParty })
                else -> ctx
            }
            return session.sendAndReceive(NotarisationPayload(tx, signature))
        }


        /**
         * The [NotarySendTransactionFlow] flow is similar to [SendTransactionFlow], but uses [NotarisationPayload] as the
         * initial message.
         */
        private class NotarySendTransactionFlow(otherSide: FlowSession, payload: NotarisationPayload) : DataVendingFlow(otherSide, payload) {
            @Suspendable
            override fun sendPayloadAndReceiveDataRequest(otherSideSession: FlowSession, payload: Any): UntrustworthyData<FetchDataFlow.Request> {
                return otherSideSession.sendAndReceive(payload)
            }
        }
    }
}

