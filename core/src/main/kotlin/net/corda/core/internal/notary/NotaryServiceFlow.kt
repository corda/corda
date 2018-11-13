package net.corda.core.internal.notary

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

/**
 * A flow run by a notary service that handles notarisation requests.
 *
 * It checks that the time-window command is valid (if present) and commits the input state, or returns a conflict
 * if any of the input states have been previously committed.
 *
 * Additional transaction validation logic can be added when implementing [validateRequest].
 */
// See AbstractStateReplacementFlow.Acceptor for why it's Void?
abstract class NotaryServiceFlow(val otherSideSession: FlowSession, val service: SinglePartyNotaryService) : FlowLogic<Void?>() {
    companion object {
        // TODO: Determine an appropriate limit and also enforce in the network parameters and the transaction builder.
        private const val maxAllowedInputsAndReferences = 10_000
    }

    private var transactionId: SecureHash? = null

    @Suspendable
    override fun call(): Void? {
        check(serviceHub.myInfo.legalIdentities.any { serviceHub.networkMapCache.isNotary(it) }) {
            "We are not a notary on the network"
        }
        val requestPayload = otherSideSession.receive<NotarisationPayload>().unwrap { it }

        try {
            val tx: TransactionParts = validateRequest(requestPayload)
            val request = NotarisationRequest(tx.inputs, tx.id)
            validateRequestSignature(request, requestPayload.requestSignature)

            verifyTransaction(requestPayload)

            // TODO Should it commit all parts?
            service.commitInputStates(
                    tx.inputs,
                    tx.id,
                    otherSideSession.counterparty,
                    requestPayload.requestSignature,
                    tx.timeWindow,
                    tx.references)
        } catch (e: NotaryInternalException) {
            logError(e.error)
            // Any exception that's not a NotaryInternalException is assumed to be an unexpected internal error
            // that is not relayed back to the client.
            throw NotaryException(e.error, transactionId)
        }

        signTransactionAndSendResponse(transactionId!!)
        return null
    }

    private fun validateRequest(requestPayload: NotarisationPayload): TransactionParts {
        try {
            val transaction = extractParts(requestPayload)
            transactionId = transaction.id
            checkNotary(transaction.notary)
            checkParametersHash(transaction.networkParametersHash)
            checkInputs(transaction.inputs + transaction.references)
            return transaction
        } catch (e: Exception) {
            val error = NotaryError.TransactionInvalid(e)
            throw NotaryInternalException(error)
        }
    }

    /** Extract the common transaction components required for notarisation. */
    protected abstract fun extractParts(requestPayload: NotarisationPayload): TransactionParts

    /** Check if transaction is intended to be signed by this notary. */
    @Suspendable
    private fun checkNotary(notary: Party?) {
        require(notary?.owningKey == service.notaryIdentityKey) {
            "The notary specified on the transaction: [$notary] does not match the notary service's identity: [${service.notaryIdentityKey}] "
        }
    }

    /** Checks whether the number of input states is too large. */
    private fun checkInputs(inputs: List<StateRef>) {
        require(inputs.size < maxAllowedInputsAndReferences) {
            "A transaction cannot have more than $maxAllowedInputsAndReferences " +
                    "inputs or references, received: ${inputs.size}"
        }
    }

    /**
     * Check that network parameters hash on this transaction is the current hash for the network.
     */
     // TODO  ENT-2666 Implement network parameters fuzzy checking. By design in Corda network we have propagation time delay.
     //     We will never end up in perfect synchronization with all the nodes. However, network parameters update process
     //     lets us predict what is the reasonable time window for changing parameters on most of the nodes.
    @Suspendable
    protected fun checkParametersHash(networkParametersHash: SecureHash?) {
        if (networkParametersHash == null && serviceHub.networkParameters.minimumPlatformVersion < 4) return
        val notaryParametersHash = serviceHub.networkParametersStorage.currentParametersHash
        require (notaryParametersHash == networkParametersHash) {
            "Transaction for notarisation was tagged with parameters with hash: $networkParametersHash, but current network parameters are: $notaryParametersHash"
        }
    }

    /** Verifies that the correct notarisation request was signed by the counterparty. */
    private fun validateRequestSignature(request: NotarisationRequest, signature: NotarisationRequestSignature) {
        val requestingParty = otherSideSession.counterparty
        request.verifySignature(signature, requestingParty)
    }

    /**
     * Override to implement custom logic to perform transaction verification based on validity and privacy requirements.
     */
    @Suspendable
    protected open fun verifyTransaction(requestPayload: NotarisationPayload) {
    }

    @Suspendable
    private fun signTransactionAndSendResponse(txId: SecureHash) {
        val signature = service.signTransaction(txId)
        otherSideSession.send(NotarisationResponse(listOf(signature)))
    }

    /**
     * The minimum amount of information needed to notarise a transaction. Note that this does not include
     * any sensitive transaction details.
     */
    protected data class TransactionParts(
            val id: SecureHash,
            val inputs: List<StateRef>,
            val timeWindow: TimeWindow?,
            val notary: Party?,
            val references: List<StateRef> = emptyList(),
            val networkParametersHash: SecureHash?
    )

    private fun logError(error: NotaryError) {
        val errorCause = when (error) {
            is NotaryError.RequestSignatureInvalid -> error.cause
            is NotaryError.TransactionInvalid ->  error.cause
            is NotaryError.General -> error.cause
            else -> null
        }
        if (errorCause != null) {
            logger.error("Error notarising transaction $transactionId", errorCause)
        }
    }
}

/** Exception internal to the notary service. Does not get exposed to CorDapps and flows calling [NotaryFlow.Client]. */
class NotaryInternalException(val error: NotaryError) : FlowException("Unable to notarise: $error")