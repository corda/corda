package net.corda.core.flows

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.SignedTransaction

/**
 * A notarisation request specifies a list of states to consume and the id of the consuming transaction. Its primary
 * purpose is for notarisation traceability – a signature over the notarisation request, [NotarisationRequestSignature],
 * allows a notary to prove that a certain party requested the consumption of a particular state.
 *
 * While the signature must be retained, the notarisation request does not need to be transferred or stored anywhere - it
 * can be built from a [SignedTransaction] or a [CoreTransaction]. The notary can recompute it from the committed states index.
 *
 * In case there is a need to prove that a party spent a particular state, the notary will:
 * 1) Locate the consuming transaction id in the index, along with all other states consumed in the same transaction.
 * 2) Build a [NotarisationRequest].
 * 3) Locate the [NotarisationRequestSignature] for the transaction id. The signature will contain the signing public key.
 * 4) Demonstrate the signature verifies against the serialized request. The provided states are always sorted internally,
 *    to ensure the serialization does not get affected by the order.
 */
@CordaSerializable
class NotarisationRequest(statesToConsume: List<StateRef>, val transactionId: SecureHash) {
    companion object {
        /** Sorts in ascending order first by transaction hash, then by output index. */
        private val stateRefComparator = compareBy<StateRef>({ it.txhash }, { it.index })
    }

    /** States this request specifies to be consumed. Sorted to ensure the serialized form does not get affected by the state order. */
    val states = statesToConsume.sortedWith(stateRefComparator)

    /** Verifies the signature against this notarisation request. Checks that the signature is issued by the right party. */
    fun verifySignature(requestSignature: NotarisationRequestSignature, intendedSigner: Party) {
        try {
            val signature = requestSignature.digitalSignature
            require(intendedSigner.owningKey == signature.by) { "Notarisation request for $transactionId not signed by the requesting party" }
            // TODO: if requestSignature was generated over an old version of NotarisationRequest, we need to be able to
            // reserialize it in that version to get the exact same bytes. Modify the serialization logic once that's
            // available.
            val expectedSignedBytes = this.serialize().bytes
            signature.verify(expectedSignedBytes)
        } catch (e: Exception) {
            val error = NotaryError.RequestSignatureInvalid(e)
            throw NotaryException(error)
        }
    }
}

/**
 * A wrapper around a digital signature used for notarisation requests.
 *
 * The [platformVersion] is required so the notary can verify the signature against the right version of serialized
 * bytes of the [NotarisationRequest]. Otherwise, the request may be rejected.
 */
@CordaSerializable
data class NotarisationRequestSignature(val digitalSignature: DigitalSignature.WithKey, val platformVersion: Int)

/**
 * Container for the transaction and notarisation request signature.
 * This is the payload that gets sent by a client to a notary service for committing the input states of the [transaction].
 */
@CordaSerializable
data class NotarisationPayload(val transaction: Any, val requestSignature: NotarisationRequestSignature) {
    init {
        require(transaction is SignedTransaction || transaction is CoreTransaction) {
            "Unsupported transaction type in the notarisation paylaod: ${transaction.javaClass.simpleName}"
        }
    }

    val signedTransaction get() = transaction as SignedTransaction
    val coreTransaction get() = transaction as CoreTransaction
}