package net.corda.core.internal.notary

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.IdempotentFlow
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.checkParameterHash
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import java.lang.IllegalStateException
import java.time.Duration

/**
 * A flow run by a notary service that handles notarisation requests.
 *
 * It checks that the time-window command is valid (if present) and commits the input state, or returns a conflict
 * if any of the input states have been previously committed.
 *
 * Additional transaction validation logic can be added when implementing [validateRequest].
 *
 * @param otherSideSession The session with the notary client.
 * @param service The notary service to utilise.
 * @param etaThreshold If the ETA for processing the request, according to the service, is greater than this, notify the client.
 */
// See AbstractStateReplacementFlow.Acceptor for why it's Void?
abstract class NotaryServiceFlow(val otherSideSession: FlowSession, val service: SinglePartyNotaryService, private val etaThreshold: Duration) : FlowLogic<Void?>(), IdempotentFlow {
    companion object {
        // TODO: Determine an appropriate limit and also enforce in the network parameters and the transaction builder.
        private const val maxAllowedInputsAndReferences = 10_000

        /**
         * This is default wait time estimate for notaries/uniqueness providers that do not estimate wait times.
         * Also used as default eta message threshold so that a default wait time/default threshold will never
         * lead to an update message being sent.
         */
        val defaultEstimatedWaitTime: Duration = 10.seconds
    }

    private var transactionId: SecureHash? = null

    @Suspendable
    private fun counterpartyCanHandleBackPressure() = otherSideSession.getCounterpartyFlowInfo(true).flowVersion >= PlatformVersionSwitches.MIN_PLATFORM_VERSION_FOR_BACKPRESSURE_MESSAGE

    @Suspendable
    override fun call(): Void? {
        val requestPayload = otherSideSession.receive<NotarisationPayload>().unwrap { it }

        val commitStatus = try {
            val tx: TransactionParts = validateRequest(requestPayload)
            val request = NotarisationRequest(tx.inputs, tx.id)
            validateRequestSignature(request, requestPayload.requestSignature)

            verifyTransaction(requestPayload)

            val eta = service.getEstimatedWaitTime(tx.inputs.size + tx.references.size)
            if (eta > etaThreshold && counterpartyCanHandleBackPressure()) {
                otherSideSession.send(WaitTimeUpdate(eta))
            }

            service.commitInputStates(
                    tx.inputs,
                    tx.id,
                    otherSideSession.counterparty,
                    requestPayload.requestSignature,
                    tx.timeWindow,
                    tx.references,
                    tx.notary)
        } catch (e: NotaryInternalException) {
            logError(e.error)
            // Any exception that's not a NotaryInternalException is assumed to be an unexpected internal error
            // that is not relayed back to the client.
            throw NotaryException(e.error, transactionId)
        }

        if (commitStatus is UniquenessProvider.Result.Success) {
            sendSignedResponse(transactionId!!, commitStatus.signature)
        }
        else {
            val error = IllegalStateException("Request that failed uniqueness reached signing code! Ignoring.")
            throw NotaryException(NotaryError.General(error))
        }
        return null
    }

    private fun validateRequest(requestPayload: NotarisationPayload): TransactionParts {
        try {
            val transaction = extractParts(requestPayload)
            transactionId = transaction.id
            logger.info("Received a notarisation request for Tx [$transactionId] from [${otherSideSession.counterparty.name}]")
            checkNotary(transaction.notary)
            checkParameterHash(transaction.networkParametersHash)
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
        require(notary?.owningKey == service.notaryIdentityKey || notary?.owningKey in service.rotatedKeys) {
            "The notary specified on the transaction: [$notary][${notary?.owningKey?.toStringShort()}] does not match the notary service's identity: [${service.notaryIdentityKey.toStringShort()}] [${service.rotatedKeys.map { it.toStringShort() }}]"
        }
    }

    /** Checks whether the number of input states is too large. */
    private fun checkInputs(inputs: List<StateRef>) {
        require(inputs.size < maxAllowedInputsAndReferences) {
            "A transaction cannot have more than $maxAllowedInputsAndReferences " +
                    "inputs or references, received: ${inputs.size}"
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
    abstract fun verifyTransaction(requestPayload: NotarisationPayload)

    @Suspendable
    private fun sendSignedResponse(txId: SecureHash, signature: TransactionSignature) {
        logger.info("Transaction [$txId] successfully notarised, sending signature back to [${otherSideSession.counterparty.name}]")
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
            is NotaryError.TransactionInvalid -> error.cause
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
