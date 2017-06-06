package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.keys
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object NotaryFlow {
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
                      override val progressTracker: ProgressTracker) : FlowLogic<List<DigitalSignature.WithKey>>() {
        constructor(stx: SignedTransaction) : this(stx, Client.tracker())

        companion object {
            object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")
            object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

            fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
        }

        lateinit var notaryParty: Party

        @Suspendable
        @Throws(NotaryException::class)
        override fun call(): List<DigitalSignature.WithKey> {
            progressTracker.currentStep = REQUESTING
            val wtx = stx.tx
            notaryParty = wtx.notary ?: throw IllegalStateException("Transaction does not specify a Notary")
            check(wtx.inputs.all { stateRef -> serviceHub.loadState(stateRef).notary == notaryParty }) {
                "Input states must have the same Notary"
            }
            try {
                stx.verifySignatures(notaryParty.owningKey)
            } catch (ex: SignedTransaction.SignaturesMissingException) {
                throw NotaryException(NotaryError.SignaturesMissing(ex))
            }

            val payload: Any = if (serviceHub.networkMapCache.isValidatingNotary(notaryParty)) {
                stx
            } else {
                wtx.buildFilteredTransaction { it is StateRef || it is TimeWindow }
            }

            val response = try {
                sendAndReceiveWithRetry<List<DigitalSignature.WithKey>>(notaryParty, payload)
            } catch (e: NotaryException) {
                if (e.error is NotaryError.Conflict) {
                    e.error.conflict.verified()
                }
                throw e
            }

            return response.unwrap { signatures ->
                signatures.forEach { validateSignature(it, stx.id.bytes) }
                signatures
            }
        }

        private fun validateSignature(sig: DigitalSignature.WithKey, data: ByteArray) {
            check(sig.by in notaryParty.owningKey.keys) { "Invalid signer for the notary result" }
            sig.verify(data)
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
    abstract class Service(val otherSide: Party,
                           val timeWindowChecker: TimeWindowChecker,
                           val uniquenessProvider: UniquenessProvider) : FlowLogic<Void?>() {
        @Suspendable
        override fun call(): Void? {
            val (id, inputs, timeWindow) = receiveAndVerifyTx()
            validateTimeWindow(timeWindow)
            commitInputStates(inputs, id)
            signAndSendResponse(id)
            return null
        }

        /**
         * Implement custom logic to receive the transaction to notarise, and perform verification based on validity and
         * privacy requirements.
         */
        @Suspendable
        abstract fun receiveAndVerifyTx(): TransactionParts

        @Suspendable
        private fun signAndSendResponse(txId: SecureHash) {
            val signature = sign(txId.bytes)
            send(otherSide, listOf(signature))
        }

        private fun validateTimeWindow(t: TimeWindow?) {
            if (t != null && !timeWindowChecker.isValid(t))
                throw NotaryException(NotaryError.TimeWindowInvalid)
        }

        /**
         * A NotaryException is thrown if any of the states have been consumed by a different transaction. Note that
         * this method does not throw an exception when input states are present multiple times within the transaction.
         */
        private fun commitInputStates(inputs: List<StateRef>, txId: SecureHash) {
            try {
                uniquenessProvider.commit(inputs, txId, otherSide)
            } catch (e: UniquenessException) {
                val conflicts = inputs.filterIndexed { i, stateRef ->
                    val consumingTx = e.error.stateHistory[stateRef]
                    consumingTx != null && consumingTx != UniquenessProvider.ConsumingTx(txId, i, otherSide)
                }
                if (conflicts.isNotEmpty()) {
                    // TODO: Create a new UniquenessException that only contains the conflicts filtered above.
                    logger.warn("Notary conflicts for $txId: $conflicts")
                    throw notaryException(txId, e)
                }
            }
        }

        private fun sign(bits: ByteArray): DigitalSignature.WithKey {
            return serviceHub.keyManagementService.sign(bits, serviceHub.notaryIdentityKey)
        }

        private fun notaryException(txId: SecureHash, e: UniquenessException): NotaryException {
            val conflictData = e.error.serialize()
            val signedConflict = SignedData(conflictData, sign(conflictData.bytes))
            return NotaryException(NotaryError.Conflict(txId, signedConflict))
        }
    }
}

/**
 * The minimum amount of information needed to notarise a transaction. Note that this does not include
 * any sensitive transaction details.
 */
data class TransactionParts(val id: SecureHash, val inputs: List<StateRef>, val timestamp: TimeWindow?)

class NotaryException(val error: NotaryError) : FlowException("Error response from Notary - $error")

@CordaSerializable
sealed class NotaryError {
    data class Conflict(val txId: SecureHash, val conflict: SignedData<UniquenessProvider.Conflict>) : NotaryError() {
        override fun toString() = "One or more input states for transaction $txId have been used in another transaction"
    }

    /** Thrown if the time specified in the [TimeWindow] command is outside the allowed tolerance. */
    object TimeWindowInvalid : NotaryError()

    data class TransactionInvalid(val msg: String) : NotaryError() {
        override fun toString() = msg
    }

    data class SignaturesInvalid(val msg: String) : NotaryError() {
        override fun toString() = msg
    }

    data class SignaturesMissing(val cause: SignedTransaction.SignaturesMissingException) : NotaryError() {
        override fun toString() = cause.toString()
    }
}
