package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaInternal
import net.corda.core.DoNotImplement
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.notary.generateSignature
import net.corda.core.internal.notary.validateSignatures
import net.corda.core.transactions.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import java.util.function.Predicate

class NotaryFlow {
    /**
     * A flow to be used by a party for obtaining signature(s) from a [NotaryService] ascertaining the transaction
     * time-window is correct and none of its inputs have been used in another completed transaction.
     *
     * In case of a single-node or Raft notary, the flow will return a single signature. For the BFT notary multiple
     * signatures will be returned – one from each replica that accepted the input state commit.
     *
     * The transaction to be notarised, [stx], should be fully verified before calling this flow.
     *
     * @throws NotaryException in case the any of the inputs to the transaction have been consumed
     *                         by another transaction or the time-window is invalid or
     *                         the parameters used for this transaction are no longer in force in the network.
     */
    @DoNotImplement
    @InitiatingFlow
    open class Client(
            private val stxs: Set<SignedTransaction>,
            override val progressTracker: ProgressTracker
    ) : BackpressureAwareTimedFlow<Map<SecureHash, List<TransactionSignature>>>() {
        constructor(stxs: Set<SignedTransaction>) : this(stxs, tracker())
        constructor(stx: SignedTransaction): this(setOf(stx), tracker())

        companion object {
            object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")
            object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

            fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
        }

        override val isTimeoutEnabled: Boolean
            @CordaInternal
            get() {
                /**
                 * We are able to only check the appropriate notary for the first transaction
                 * as we have already checked that all transactions specify the same notary in the [CheckTransaction] method.
                 */

                val firstTransaction = stxs.firstOrNull() ?: throw IllegalStateException("There are no transactions to check.")
                val notaryParty = firstTransaction.notary ?: throw IllegalStateException("Transaction does not specify a Notary")
                return serviceHub.networkMapCache.getNodesByLegalIdentityKey(notaryParty.owningKey).size > 1
            }

        @Suspendable
        @Throws(NotaryException::class)
        override fun call(): Map<SecureHash, List<TransactionSignature>> {
            stxs.first().pushToLoggingContext()
            val notaryParty = checkTransaction()
            logger.info("Sending transaction[s] to notary: ${notaryParty.name}.")
            progressTracker.currentStep = REQUESTING
            val response = notarise(notaryParty)
            logger.info("Notary responded.")
            progressTracker.currentStep = VALIDATING
            return validateResponse(response, notaryParty)
        }

        /**
         * Checks that the transaction specifies a valid notary, and verifies that it contains all required signatures
         * apart from the notary's.
         */
        // TODO: [CORDA-2274] Perform full transaction verification once verification caching is enabled.
        protected fun checkTransaction(): Party {

            val notaryParty = stxs.first().notary ?: throw IllegalStateException("Transaction does not specify a Notary")
            require(stxs.filter { it.notary != notaryParty }.isEmpty()) {
                "Batched transactions must all reference to a single Notary"
            }

            val stx = stxs.first()

            check(serviceHub.networkMapCache.isNotary(notaryParty)) { "$notaryParty is not a notary on the network" }
            check(serviceHub.loadStates(stx.inputs.toSet() + stx.references.toSet()).all { it.state.notary == notaryParty }) {
                "Input states and reference input states must have the same Notary"
            }
            stx.resolveTransactionWithSignatures(serviceHub).verifySignaturesExcept(notaryParty.owningKey)
            return notaryParty

        }
        /** Notarises the transaction with the [notaryParty], obtains the notary's signature(s). */
        @Throws(NotaryException::class)
        @Suspendable
        protected fun notarise(notaryParty: Party): UntrustworthyData<NotarisationResponse> {
            val session = initiateFlow(notaryParty)
            val requestSignature = generateRequestSignature()
            return if (isValidating(notaryParty)) {
                sendAndReceiveValidating(session, requestSignature)
            } else {
                sendAndReceiveNonValidating(notaryParty, session, requestSignature)
            }
        }

        private fun isValidating(notaryParty: Party): Boolean {
            val onTheCurrentWhitelist = serviceHub.networkMapCache.isNotary(notaryParty)
            return if (!onTheCurrentWhitelist) {
                /*
                    Note that the only scenario where it's acceptable to use a notary not in the current network parameter whitelist is
                    when performing a notary change transaction after a network merge – the old notary won't be on the whitelist of the new network,
                    and can't be used for regular transactions.
                */
                check(stxs.first().coreTransaction is NotaryChangeWireTransaction) {
                    "Notary $notaryParty is not on the network parameter whitelist. A non-whitelisted notary can only be used for notary change transactions"
                }
                val historicNotary = (serviceHub.networkParametersService as NetworkParametersStorage).getHistoricNotary(notaryParty)
                        ?: throw IllegalStateException("The notary party $notaryParty specified by transaction ${stxs.first().id}, is not recognised as a current or historic notary.")
                historicNotary.validating

            } else serviceHub.networkMapCache.isValidatingNotary(notaryParty)
        }

        @Suspendable
        private fun sendAndReceiveValidating(session: FlowSession, signature: NotarisationRequestSignature): UntrustworthyData<NotarisationResponse> {
            val payload = NotarisationPayload(stxs, signature)
            subFlow(NotarySendTransactionFlow(session, payload))
            return receiveResultOrTiming(session)
        }

        @Suspendable
        private fun sendAndReceiveNonValidating(notaryParty: Party, session: FlowSession, signature: NotarisationRequestSignature): UntrustworthyData<NotarisationResponse> {
            val txs = stxs.map { stx ->
                val ctx = stx.coreTransaction
                when (ctx) {
                    is ContractUpgradeWireTransaction -> ctx.buildFilteredTransaction()
                    is WireTransaction -> ctx.buildFilteredTransaction(Predicate {
                        it is StateRef || it is ReferenceStateRef || it is TimeWindow || it == notaryParty || it is NetworkParametersHash
                    })
                    else -> ctx
                }
            }.toSet()

            session.send(NotarisationPayload(txs, signature))
            return receiveResultOrTiming(session)
        }

        /** Checks that the notary's signature(s) is/are valid. */
        protected fun validateResponse(response: UntrustworthyData<NotarisationResponse>, notaryParty: Party): Map<SecureHash, List<TransactionSignature>> {
            return response.unwrap {
                it.signatures.forEach { id, signatures ->
                    it.validateSignatures(id, signatures, notaryParty)
                }
                it.signatures
            }
        }

        /**
         * The [NotarySendTransactionFlow] flow is similar to [SendTransactionFlow], but uses [NotarisationPayload] as the
         * initial message, and retries message delivery.
         */
        private class NotarySendTransactionFlow(otherSide: FlowSession, payload: NotarisationPayload) : DataVendingFlow(otherSide, payload) {
            @Suspendable
            override fun sendPayloadAndReceiveDataRequest(otherSideSession: FlowSession, payload: Any): UntrustworthyData<FetchDataFlow.Request> {
                return otherSideSession.sendAndReceiveWithRetry(payload)
            }
        }

        /**
         * Ensure that transaction ID instances are not referenced in the serialized form in case several input states are outputs of the
         * same transaction.
         */
        private fun generateRequestSignature(): NotarisationRequestSignature {
            // TODO: This is not required any more once our AMQP serialization supports turning off object referencing.
            var dataForNotarisationRequestion = arrayListOf<StateRef>()
            stxs.forEach { stx ->
                dataForNotarisationRequestion.addAll(stx.inputs.map { it.copy(txhash = SecureHash.parse(it.txhash.toString())) })
            }
            val notarisationRequest = NotarisationRequest(dataForNotarisationRequestion)
            return notarisationRequest.generateSignature(serviceHub)
        }
    }
}
