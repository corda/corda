package net.corda.core.transactions

import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.signWithECDSA
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializedBytes
import java.security.KeyPair
import java.security.SignatureException
import java.util.*

/**
 * SignedTransaction wraps a serialized WireTransaction. It contains one or more signatures, each one for
 * a public key that is mentioned inside a transaction command. SignedTransaction is the top level transaction type
 * and the type most frequently passed around the network and stored. The identity of a transaction is the hash
 * of a WireTransaction, therefore if you are storing data keyed by WT hash be aware that multiple different STs may
 * map to the same key (and they could be different in important ways, like validity!). The signatures on a
 * SignedTransaction might be invalid or missing: the type does not imply validity.
 * A transaction ID should be the hash of the [WireTransaction] Merkle tree root. Thus adding or removing a signature does not change it.
 */
data class SignedTransaction(val txBits: SerializedBytes<WireTransaction>,
                             val sigs: List<DigitalSignature.WithKey>,
                             override val id: SecureHash
) : NamedByHash {
    init {
        require(sigs.isNotEmpty())
    }

    // TODO: This needs to be reworked to ensure that the inner WireTransaction is only ever deserialised sandboxed.

    /** Lazily calculated access to the deserialised/hashed transaction data. */
    val tx: WireTransaction by lazy {
        val temp = WireTransaction.deserialize(txBits)
        check(temp.id == id) { "Supplied transaction ID does not match deserialized transaction's ID - this is probably a problem in serialization/deserialization" }
        temp
    }

    class SignaturesMissingException(val missing: Set<CompositeKey>, val descriptions: List<String>, override val id: SecureHash) : NamedByHash, SignatureException() {
        override fun toString(): String {
            return "Missing signatures for $descriptions on transaction ${id.prefixChars()} for ${missing.joinToString()}"
        }
    }

    /**
     * Verifies the signatures on this transaction and throws if any are missing which aren't passed as parameters.
     * In this context, "verifying" means checking they are valid signatures and that their public keys are in
     * the contained transactions [BaseTransaction.mustSign] property.
     *
     * Normally you would not provide any keys to this function, but if you're in the process of building a partial
     * transaction and you want to access the contents before you've signed it, you can specify your own keys here
     * to bypass that check.
     *
     * @throws SignatureException if any signatures are invalid or unrecognised.
     * @throws SignaturesMissingException if any signatures should have been present but were not.
     */
    @Throws(SignatureException::class)
    fun verifySignatures(vararg allowedToBeMissing: CompositeKey): WireTransaction {
        // Embedded WireTransaction is not deserialised until after we check the signatures.
        checkSignaturesAreValid()

        val missing = getMissingSignatures()
        if (missing.isNotEmpty()) {
            val allowed = setOf(*allowedToBeMissing)
            val needed = missing - allowed
            if (needed.isNotEmpty())
                throw SignaturesMissingException(needed, getMissingKeyDescriptions(needed), id)
        }
        check(tx.id == id)
        return tx
    }

    /**
     * Mathematically validates the signatures that are present on this transaction. This does not imply that
     * the signatures are by the right keys, or that there are sufficient signatures, just that they aren't
     * corrupt. If you use this function directly you'll need to do the other checks yourself. Probably you
     * want [verifySignatures] instead.
     *
     * @throws SignatureException if a signature fails to verify.
     */
    @Throws(SignatureException::class)
    fun checkSignaturesAreValid() {
        for (sig in sigs) {
            sig.verifyWithECDSA(id.bytes)
        }
    }

    private fun getMissingSignatures(): Set<CompositeKey> {
        val sigKeys = sigs.map { it.by }.toSet()
        val missing = tx.mustSign.filter { !it.isFulfilledBy(sigKeys) }.toSet()
        return missing
    }

    /**
     * Get a human readable description of where signatures are required from, and are missing, to assist in debugging
     * the underlying cause.
     */
    private fun getMissingKeyDescriptions(missing: Set<CompositeKey>): ArrayList<String> {
        // TODO: We need a much better way of structuring this data
        val missingElements = ArrayList<String>()
        this.tx.commands.forEach { command ->
            if (command.signers.any { it in missing })
                missingElements.add(command.toString())
        }
        if (this.tx.notary?.owningKey in missing)
            missingElements.add("notary")
        return missingElements
    }

    /** Returns the same transaction but with an additional (unchecked) signature. */
    fun withAdditionalSignature(sig: DigitalSignature.WithKey) = copy(sigs = sigs + sig)

    /** Returns the same transaction but with an additional (unchecked) signatures. */
    fun withAdditionalSignatures(sigList: Iterable<DigitalSignature.WithKey>) = copy(sigs = sigs + sigList)

    /** Alias for [withAdditionalSignature] to let you use Kotlin operator overloading. */
    operator fun plus(sig: DigitalSignature.WithKey) = withAdditionalSignature(sig)

    /** Alias for [withAdditionalSignatures] to let you use Kotlin operator overloading. */
    operator fun plus(sigList: Collection<DigitalSignature.WithKey>) = withAdditionalSignatures(sigList)

    /**
     * Calls [verifySignatures] to check all required signatures are present, and then calls
     * [WireTransaction.toLedgerTransaction] with the passed in [ServiceHub] to resolve the dependencies,
     * returning an unverified LedgerTransaction.
     *
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     * @throws SignatureException if any signatures were invalid or unrecognised
     * @throws SignaturesMissingException if any signatures that should have been present are missing.
     */
    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class, SignatureException::class)
    fun toLedgerTransaction(services: ServiceHub) = verifySignatures().toLedgerTransaction(services)

    /**
     * Utility to simplify the act of signing the transaction.
     *
     * @param keyPair the signer's public/private key pair.
     *
     * @return a digital signature of the transaction.
     */
    fun signWithECDSA(keyPair: KeyPair) = keyPair.signWithECDSA(this.id.bytes)
}
