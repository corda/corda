package net.corda.core.transactions

import net.corda.core.contracts.NamedByHash
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.transactions.SignedTransaction.SignaturesMissingException
import net.corda.core.utilities.toNonEmptySet
import java.security.PublicKey
import java.security.SignatureException

/** An interface for transactions containing signatures, with logic for signature verification */
interface TransactionWithSignatures : NamedByHash {
    val sigs: List<DigitalSignature.WithKey>

    /** Specifies all the public keys that require signatures for the transaction to be valid */
    val requiredSigningKeys: Set<PublicKey>

    /**
     * Verifies the signatures on this transaction and throws if any are missing. In this context, "verifying" means
     * checking they are valid signatures and that their public keys are in the contained transactions
     * [BaseTransaction.mustSign] property.
     *
     * @throws SignatureException if any signatures are invalid or unrecognised.
     * @throws SignaturesMissingException if any signatures should have been present but were not.
     */
    @Throws(SignatureException::class)
    fun verifyRequiredSignatures() = verifySignaturesExcept()

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
    // DOCSTART 2
    @Throws(SignatureException::class)
    fun verifySignaturesExcept(vararg allowedToBeMissing: PublicKey) {
        // DOCEND 2
        // Embedded WireTransaction is not deserialised until after we check the signatures.
        checkSignaturesAreValid()

        val needed = getMissingSignatures() - allowedToBeMissing
        if (needed.isNotEmpty())
            throw SignaturesMissingException(needed.toNonEmptySet(), getKeyDescriptions(needed), id)
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
            sig.verify(id.bytes)
        }
    }

    /** Provides a textual description for each of the [keys], which is helpful for error scenarios */
    fun getKeyDescriptions(keys: Set<PublicKey>): List<String>

    private fun getMissingSignatures(): Set<PublicKey> {
        val sigKeys = sigs.map { it.by }.toSet()
        // TODO Problem is that we can get single PublicKey wrapped as CompositeKey in allowedToBeMissing/mustSign
        //  equals on CompositeKey won't catch this case (do we want to single PublicKey be equal to the same key wrapped in CompositeKey with threshold 1?)
        val missing = requiredSigningKeys.filter { !it.isFulfilledBy(sigKeys) }.toSet()
        return missing
    }
}