package net.corda.notaryhealthcheck.cordapp

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.flows.NotaryFlow.Client.Companion.REQUESTING
import net.corda.core.flows.NotaryFlow.Client.Companion.VALIDATING
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.notary.generateSignature
import net.corda.core.transactions.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.node.services.api.ServiceHubInternal
import net.corda.notaryhealthcheck.contract.NullContract
import net.corda.notaryhealthcheck.utils.Monitorable
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Predicate

@StartableByService
class HealthCheckFlow(monitorable: Monitorable) : FlowLogic<List<TransactionSignature>>() {
    private val notary = monitorable.notary
    private val party = monitorable.party

    companion object {
        object PREPARING : ProgressTracker.Step("Preparing")
        object CHECKING : ProgressTracker.Step("Checking")
    }

    override val progressTracker = ProgressTracker(PREPARING, CHECKING)

    @Suspendable
    override fun call(): List<TransactionSignature> {
        progressTracker.currentStep = PREPARING
        val stx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
            addOutputState(NullContract.State(listOf(ourIdentity)), NullContract::class.java.name, AlwaysAcceptAttachmentConstraint)
            addCommand(NullContract.NullCommand(), listOf(ourIdentity.owningKey))
        })
        progressTracker.currentStep = CHECKING
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

        // Notary health checks should never be retried - they are run on a schedule anyway.
        override val isTimeoutEnabled: Boolean = false

        @Suspendable
        @Throws(NotaryException::class)
        override fun call(): List<TransactionSignature> {
            val session = initiateFlow(party)
            val requestSignature = NotarisationRequest(stx.inputs, stx.id).generateSignature(serviceHub)
            progressTracker.currentStep = REQUESTING
            setWaitTimeGauge((serviceHub as ServiceHubInternal).configuration.flowTimeout.timeout.seconds)
            val result = if (serviceHub.networkMapCache.isValidatingNotary(notaryParty)) {
                sendAndReceiveValidating(session, requestSignature)
            } else {
                sendAndReceiveNonValidating(notaryParty, session, requestSignature)
            }
            progressTracker.currentStep = VALIDATING
            return validateResponse(result, notaryParty)
        }

        class WaitTimeLatchedGauge(var currentWaitTime: AtomicLong) : Gauge<Long> {
            override fun getValue(): Long {
                return currentWaitTime.get()
            }
        }

        private fun setWaitTimeGauge(value: Long) {
            val name = MetricRegistry.name(Metrics.reportedWaitTimeSeconds(party.metricPrefix()))
            val gauge = (serviceHub as ServiceHubInternal).monitoringService.metrics.gauge(name, { WaitTimeLatchedGauge(AtomicLong(value)) })
            (gauge as WaitTimeLatchedGauge).currentWaitTime.set(value)
        }

        @Suspendable
        private fun sendAndReceiveValidating(session: FlowSession, signature: NotarisationRequestSignature): UntrustworthyData<NotarisationResponse> {
            val payload = NotarisationPayload(stx, signature)
            subFlow(NotarySendTransactionFlow(session, payload))
            return receiveResultOrTiming(session)
        }

        @Suspendable
        private fun sendAndReceiveNonValidating(notaryParty: Party, session: FlowSession, signature: NotarisationRequestSignature): UntrustworthyData<NotarisationResponse> {
            val ctx = stx.coreTransaction
            val tx = when (ctx) {
                is ContractUpgradeWireTransaction -> ctx.buildFilteredTransaction()
                is WireTransaction -> ctx.buildFilteredTransaction(Predicate {
                    it is StateRef || it is ReferenceStateRef || it is TimeWindow || it == notaryParty || it is NetworkParametersHash
                })
                else -> ctx
            }
            session.send(NotarisationPayload(tx, signature))
            return receiveResultOrTiming(session)
        }

        override fun applyWaitTimeUpdate(session: FlowSession, update: WaitTimeUpdate) {
            setWaitTimeGauge(update.waitTime.seconds)
            super.applyWaitTimeUpdate(session, update)
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