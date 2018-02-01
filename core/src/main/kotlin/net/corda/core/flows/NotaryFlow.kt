package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.keys
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.node.services.NotaryService
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import java.security.SignatureException
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
            check(stx.inputs.all { stateRef -> serviceHub.loadState(stateRef).notary == notaryParty }) {
                "Input states must have the same Notary"
            }

            try {
                stx.resolveTransactionWithSignatures(serviceHub).verifySignaturesExcept(notaryParty.owningKey)
            } catch (ex: SignatureException) {
                throw NotaryException(NotaryError.TransactionInvalid(ex))
            }
            return notaryParty
        }

        @Throws(NotaryException::class)
        @Suspendable
        protected fun notarise(notaryParty: Party): UntrustworthyData<List<TransactionSignature>> {
            return try {
                val session = initiateFlow(notaryParty)
                if (serviceHub.networkMapCache.isValidatingNotary(notaryParty)) {
                    sendAndReceiveValidating(session)
                } else {
                    sendAndReceiveNonValidating(notaryParty, session)
                }
            } catch (e: NotaryException) {
                if (e.error is NotaryError.Conflict) {
                    e.error.conflict.verified()
                }
                throw e
            }
        }

        @Suspendable
        protected open fun sendAndReceiveValidating(session: FlowSession): UntrustworthyData<List<TransactionSignature>> {
            subFlow(SendTransactionWithRetry(session, stx))
            return session.receive()
        }

        @Suspendable
        protected open fun sendAndReceiveNonValidating(notaryParty: Party, session: FlowSession): UntrustworthyData<List<TransactionSignature>> {
            val tx: Any = if (stx.isNotaryChangeTransaction()) {
                stx.notaryChangeTx // Notary change transactions do not support filtering
            } else {
                stx.buildFilteredTransaction(Predicate { it is StateRef || it is TimeWindow || it == notaryParty })
            }
            return session.sendAndReceiveWithRetry(tx)
        }

        protected fun validateResponse(response: UntrustworthyData<List<TransactionSignature>>, notaryParty: Party): List<TransactionSignature> {
            return response.unwrap { signatures ->
                signatures.forEach { validateSignature(it, stx.id, notaryParty) }
                signatures
            }
        }

        private fun validateSignature(sig: TransactionSignature, txId: SecureHash, notaryParty: Party) {
            check(sig.by in notaryParty.owningKey.keys) { "Invalid signer for the notary result" }
            sig.verify(txId)
        }
    }

    /**
     * The [SendTransactionWithRetry] flow is equivalent to [SendTransactionFlow] but using [sendAndReceiveWithRetry]
     * instead of [sendAndReceive], [SendTransactionWithRetry] is intended to be use by the notary client only.
     */
    private class SendTransactionWithRetry(otherSideSession: FlowSession, stx: SignedTransaction) : SendTransactionFlow(otherSideSession, stx) {
        @Suspendable
        override fun sendPayloadAndReceiveDataRequest(otherSideSession: FlowSession, payload: Any): UntrustworthyData<FetchDataFlow.Request> {
            return otherSideSession.sendAndReceiveWithRetry(payload)
        }
    }

    /**
     * A flow run by a notary service that handles notarisation requests.
     *
     * It checks that the time-window command is valid (if present) and commits the input state, or returns a conflict
     * if any of the input states have been previously committed.
     *
     * Additional transaction validation logic can be added when implementing [receiveAndVerifyTx].
     */
    // See AbstractStateReplacementFlow.Acceptor for why it's Void?
    abstract class Service(val otherSideSession: FlowSession, val service: TrustedAuthorityNotaryService) : FlowLogic<Void?>() {

        @Suspendable
        override fun call(): Void? {
            check(serviceHub.myInfo.legalIdentities.any { serviceHub.networkMapCache.isNotary(it) }) {
                "We are not a notary on the network"
            }
            val (id, inputs, timeWindow, notary) = receiveAndVerifyTx()
            checkNotary(notary)
            service.validateTimeWindow(timeWindow)
            service.commitInputStates(inputs, id, otherSideSession.counterparty)
            signAndSendResponse(id)
            return null
        }

        /**
         * Implement custom logic to receive the transaction to notarise, and perform verification based on validity and
         * privacy requirements.
         */
        @Suspendable
        abstract fun receiveAndVerifyTx(): TransactionParts

        // Check if transaction is intended to be signed by this notary.
        @Suspendable
        protected fun checkNotary(notary: Party?) {
            // TODO This check implies that it's OK to use the node's main identity. Shouldn't it be just limited to the
            // notary identities?
            if (notary == null || !serviceHub.myInfo.isLegalIdentity(notary)) {
                throw NotaryException(NotaryError.WrongNotary)
            }
        }

        @Suspendable
        private fun signAndSendResponse(txId: SecureHash) {
            val signature = service.sign(txId)
            otherSideSession.send(listOf(signature))
        }
    }
}

/**
 * The minimum amount of information needed to notarise a transaction. Note that this does not include
 * any sensitive transaction details.
 */
data class TransactionParts(val id: SecureHash, val inputs: List<StateRef>, val timestamp: TimeWindow?, val notary: Party?)

class NotaryException(val error: NotaryError) : FlowException("Unable to notarise: $error")

@CordaSerializable
sealed class NotaryError {
    data class Conflict(val txId: SecureHash, val conflict: SignedData<UniquenessProvider.Conflict>) : NotaryError() {
        override fun toString() = "One or more input states for transaction $txId have been used in another transaction"
    }

    /** Occurs when time specified in the [TimeWindow] command is outside the allowed tolerance. */
    data class TimeWindowInvalid(val currentTime: Instant, val txTimeWindow: TimeWindow) : NotaryError() {
        override fun toString() = "Current time $currentTime is outside the time bounds specified by the transaction: $txTimeWindow"

        companion object {
            @JvmField @Deprecated("Here only for binary compatibility purposes, do not use.")
            val INSTANCE = TimeWindowInvalid(Instant.EPOCH, TimeWindow.fromOnly(Instant.EPOCH))
        }
    }

    data class TransactionInvalid(val cause: Throwable) : NotaryError() {
        override fun toString() = cause.toString()
    }

    object WrongNotary : NotaryError()

    data class General(val cause: String): NotaryError() {
        override fun toString() = cause
    }
}
