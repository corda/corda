package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DoNotImplement
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.generateSignature
import net.corda.core.internal.validateSignatures
import net.corda.core.node.services.NotaryService
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import java.time.Instant
import java.util.function.Predicate

class NotaryFlow {
    /**
     * A flow to be used by a party for obtaining signature(s) from a [NotaryService] ascertaining the transaction
     * time-window is correct and none of its inputs have been used in another completed transaction.
     *
     * In case of a single-node or Raft notary, the flow will return a single signature. For the BFT notary multiple
     * signatures will be returned – one from each replica that accepted the input state commit.
     *
     * @throws NotaryException in case the any of the inputs to the transaction have been consumed
     *                         by another transaction or the time-window is invalid.
     */
    @DoNotImplement
    @InitiatingFlow
    open class Client(private val stx: SignedTransaction,
                      override val progressTracker: ProgressTracker) : FlowLogic<List<TransactionSignature>>() {
        constructor(stx: SignedTransaction) : this(stx, tracker())

        companion object {
            object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")
            object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

            fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
        }

        @Suspendable
        @Throws(NotaryException::class)
        override fun call(): List<TransactionSignature> {
            val notaryParty = checkTransaction()
            progressTracker.currentStep = REQUESTING
            val response = notarise(notaryParty)
            progressTracker.currentStep = VALIDATING
            return validateResponse(response, notaryParty)
        }

        /**
         * Checks that the transaction specifies a valid notary, and verifies that it contains all required signatures
         * apart from the notary's.
         */
        protected fun checkTransaction(): Party {
            val notaryParty = stx.notary ?: throw IllegalStateException("Transaction does not specify a Notary")
            check(serviceHub.networkMapCache.isNotary(notaryParty)) { "$notaryParty is not a notary on the network" }
            check(serviceHub.loadStates(stx.inputs.toSet()).all { it.state.notary == notaryParty }) {
                "Input states must have the same Notary"
            }
            stx.resolveTransactionWithSignatures(serviceHub).verifySignaturesExcept(notaryParty.owningKey)
            return notaryParty
        }

        /** Notarises the transaction with the [notaryParty], obtains the notary's signature(s). */
        @Throws(NotaryException::class)
        @Suspendable
        protected fun notarise(notaryParty: Party): UntrustworthyData<NotarisationResponse> {
            val session = initiateFlow(notaryParty)
            val requestSignature = NotarisationRequest(stx.inputs, stx.id).generateSignature(serviceHub)
            return if (serviceHub.networkMapCache.isValidatingNotary(notaryParty)) {
                sendAndReceiveValidating(session, requestSignature)
            } else {
                sendAndReceiveNonValidating(notaryParty, session, requestSignature)
            }
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
            return session.sendAndReceiveWithRetry(NotarisationPayload(tx, signature))
        }

        /** Checks that the notary's signature(s) is/are valid. */
        protected fun validateResponse(response: UntrustworthyData<NotarisationResponse>, notaryParty: Party): List<TransactionSignature> {
            return response.unwrap {
                it.validateSignatures(stx.id, notaryParty)
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
    }

    /**
     * A flow run by a notary service that handles notarisation requests.
     *
     * It checks that the time-window command is valid (if present) and commits the input state, or returns a conflict
     * if any of the input states have been previously committed.
     *
     * Additional transaction validation logic can be added when implementing [validateRequest].
     */
    // See AbstractStateReplacementFlow.Acceptor for why it's Void?
    abstract class Service(val otherSideSession: FlowSession, val service: TrustedAuthorityNotaryService) : FlowLogic<Void?>() {
        companion object {
            // TODO: Determine an appropriate limit and also enforce in the network parameters and the transaction builder.
            private const val maxAllowedInputs = 10_000
        }

        @Suspendable
        override fun call(): Void? {
            check(serviceHub.myInfo.legalIdentities.any { serviceHub.networkMapCache.isNotary(it) }) {
                "We are not a notary on the network"
            }
            val requestPayload = otherSideSession.receive<NotarisationPayload>().unwrap { it }
            var txId: SecureHash? = null
            try {
                val parts = validateRequest(requestPayload)
                txId = parts.id
                checkNotary(parts.notary)
                service.commitInputStates(parts.inputs, txId, otherSideSession.counterparty, requestPayload.requestSignature, parts.timestamp)
                signTransactionAndSendResponse(txId)
            } catch (e: NotaryInternalException) {
                throw NotaryException(e.error, txId)
            }
            return null
        }

        /** Checks whether the number of input states is too large. */
        protected fun checkInputs(inputs: List<StateRef>) {
            if (inputs.size > maxAllowedInputs) {
                val error = NotaryError.TransactionInvalid(
                        IllegalArgumentException("A transaction cannot have more than $maxAllowedInputs inputs, received: ${inputs.size}")
                )
                throw NotaryInternalException(error)
            }
        }

        /**
         * Implement custom logic to perform transaction verification based on validity and privacy requirements.
         */
        @Suspendable
        protected abstract fun validateRequest(requestPayload: NotarisationPayload): TransactionParts

        /** Check if transaction is intended to be signed by this notary. */
        @Suspendable
        protected fun checkNotary(notary: Party?) {
            if (notary?.owningKey != service.notaryIdentityKey) {
                throw NotaryInternalException(NotaryError.WrongNotary)
            }
        }

        @Suspendable
        private fun signTransactionAndSendResponse(txId: SecureHash) {
            val signature = service.sign(txId)
            otherSideSession.send(NotarisationResponse(listOf(signature)))
        }
    }
}

/**
 * The minimum amount of information needed to notarise a transaction. Note that this does not include
 * any sensitive transaction details.
 */
data class TransactionParts(val id: SecureHash, val inputs: List<StateRef>, val timestamp: TimeWindow?, val notary: Party?)

/**
 * Exception thrown by the notary service if any issues are encountered while trying to commit a transaction. The
 * underlying [error] specifies the cause of failure.
 */
class NotaryException(
        /** Cause of notarisation failure. */
        val error: NotaryError,
        /** Id of the transaction to be notarised. Can be _null_ if an error occurred before the id could be resolved. */
        val txId: SecureHash? = null
) : FlowException("Unable to notarise transaction${txId ?: " "}: $error")

/** Exception internal to the notary service. Does not get exposed to CorDapps and flows calling [NotaryFlow.Client]. */
class NotaryInternalException(val error: NotaryError) : FlowException("Unable to notarise: $error")

/** Specifies the cause for notarisation request failure. */
@CordaSerializable
sealed class NotaryError {
    /** Occurs when one or more input states have already been consumed by another transaction. */
    data class Conflict(
            /** Id of the transaction that was attempted to be notarised. */
            val txId: SecureHash,
            /** Specifies which states have already been consumed in another transaction. */
            val consumedStates: Map<StateRef, StateConsumptionDetails>
    ) : NotaryError() {
        override fun toString() = "One or more input states have been used in another transaction"
    }

    /** Occurs when time specified in the [TimeWindow] command is outside the allowed tolerance. */
    data class TimeWindowInvalid(val currentTime: Instant, val txTimeWindow: TimeWindow) : NotaryError() {
        override fun toString() = "Current time $currentTime is outside the time bounds specified by the transaction: $txTimeWindow"

        companion object {
            @JvmField
            @Deprecated("Here only for binary compatibility purposes, do not use.")
            val INSTANCE = TimeWindowInvalid(Instant.EPOCH, TimeWindow.fromOnly(Instant.EPOCH))
        }
    }

    /** Occurs when the provided transaction fails to verify. */
    data class TransactionInvalid(val cause: Throwable) : NotaryError() {
        override fun toString() = cause.toString()
    }

    /** Occurs when the transaction sent for notarisation is assigned to a different notary identity. */
    object WrongNotary : NotaryError()

    /** Occurs when the notarisation request signature does not verify for the provided transaction. */
    data class RequestSignatureInvalid(val cause: Throwable) : NotaryError() {
        override fun toString() = "Request signature invalid: $cause"
    }

    /** Occurs when the notary service encounters an unexpected issue or becomes temporarily unavailable. */
    data class General(val cause: Throwable) : NotaryError() {
        override fun toString() = cause.toString()
    }
}

/** Contains information about the consuming transaction for a particular state. */
// TODO: include notary timestamp?
@CordaSerializable
data class StateConsumptionDetails(
        /**
         * Hash of the consuming transaction id.
         *
         * Note that this is NOT the transaction id itself – revealing it could lead to privacy leaks.
         */
        val hashOfTransactionId: SecureHash
)