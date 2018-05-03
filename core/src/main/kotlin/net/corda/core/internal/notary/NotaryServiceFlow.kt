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
abstract class NotaryServiceFlow(val otherSideSession: FlowSession, val service: TrustedAuthorityNotaryService) : FlowLogic<Void?>() {
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
            service.commitInputStates(parts.inputs, txId, otherSideSession.counterparty, requestPayload.requestSignature, parts.timestamp, parts.references)
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

    /** Verifies that the correct notarisation request was signed by the counterparty. */
    protected fun validateRequestSignature(request: NotarisationRequest, signature: NotarisationRequestSignature) {
        val requestingParty = otherSideSession.counterparty
        request.verifySignature(signature, requestingParty)
    }

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

    /**
     * The minimum amount of information needed to notarise a transaction. Note that this does not include
     * any sensitive transaction details.
     */
    protected data class TransactionParts @JvmOverloads constructor(
            val id: SecureHash,
            val inputs: List<StateRef>,
            val timestamp: TimeWindow?,
            val notary: Party?,
            val references: List<StateRef> = emptyList()
    ) {
        fun copy(id: SecureHash, inputs: List<StateRef>, timestamp: TimeWindow?, notary: Party?): TransactionParts {
            return TransactionParts(id, inputs, timestamp, notary, references)
        }
    }
}

/** Exception internal to the notary service. Does not get exposed to CorDapps and flows calling [NotaryFlow.Client]. */
class NotaryInternalException(val error: NotaryError) : FlowException("Unable to notarise: $error")