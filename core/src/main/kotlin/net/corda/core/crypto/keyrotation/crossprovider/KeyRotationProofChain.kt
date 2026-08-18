package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.toStringShort
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.loggerFor
import java.security.GeneralSecurityException
import java.security.PublicKey

/**
 * Represents an ordered chain of cross-provider key-rotation proofs.
 *
 * Each element proves that `publicKeyOld` authorized a rotation to `publicKeyNew`
 * by signing the new key bytes. As a chain, it provides a verifiable lineage from
 * an original key to the current key, as long as every link is continuous and
 * cryptographically valid.
 */
@CordaSerializable
data class KeyRotationProofChain(private val proofChain: List<KeyRotationProof>) {

    companion object {
        private val logger = loggerFor<KeyRotationProofChain>()
    }

    val originalKey: PublicKey
        get() = proofChain.first().publicKeyOld

    val currentKey: PublicKey
        get() = proofChain.last().publicKeyNew

    fun isEmpty() = proofChain.isEmpty()

    fun isNotEmpty() = !isEmpty()

    fun size() = proofChain.size

    fun getKeyLineage(): List<PublicKey> {
        if (isEmpty()) {
            return emptyList()
        }

        val keys = mutableListOf<PublicKey>()
        keys.add(originalKey)

        proofChain.forEach { proof ->
            keys.add(proof.publicKeyNew) // Add the new key from each proof
        }

        return keys
    }

    fun isValid(currentKey: PublicKey): Boolean {
        if(isEmpty()) {
            return true
        }

        return isValid(originalKey, currentKey)
    }

    fun asList(): List<KeyRotationProof> = proofChain

    /**
     * Checks that a key rotation proof chain is valid.
     *
     * The chain is valid if:
     * 1. It starts with the original key.
     * 2. Each key change follows the previous one (continuous chain).
     * 3. It ends with the current key.
     * 4. Each key change is signed by the previous key.
     *
     * This ensures the history of key rotations is complete and trustworthy.
     */
    @Suppress("ComplexMethod")
    fun isValid(originalKey: PublicKey, currentKey: PublicKey): Boolean {
        if (isEmpty()) {
            logger.warn("Validation failed. Chain is empty. Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
            return false
        }

        // Validate first proof
        if(!startsWithKey(originalKey)){
            logger.warn("Validation failed. The chain does not start with the expected original key. Expected: ${originalKey.toStringShort()}, Actual: ${this.originalKey.toStringShort()}, Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
            return false
        }

        if(!isValid(proofChain[0])){
            logger.warn("Validation failed. Proof is invalid. Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
            return false
        }

        // Track keys as the chain is processed: a key may appear only once, so a self-proof
        // (A -> A) or a loop (A -> B -> A) is rejected on the offending proof.
        val seenKeys = hashSetOf(proofChain[0].publicKeyOld)
        if(!seenKeys.add(proofChain[0].publicKeyNew)) {
            logger.warn("Validation failed. The chain revisits a key; each key must appear only once. Repeated key: ${proofChain[0].publicKeyNew.toStringShort()}, Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
            return false
        }

        // Validate intermediate proofChain (continuity + proof)
        proofChain.zipWithNext().forEach { (previous, current) ->
            if(!isContinuous(previous, current)) {
                logger.warn("Validation failed. The chain is not continuous. Previous proof new key and current proof old key must match. Previous proof new key: ${previous.publicKeyOld.toStringShort()}, Current proof old key: ${current.publicKeyOld.toStringShort()}, Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
                return false
            }

            if(!isValid(current)) {
                logger.warn("Validation failed. Proof is invalid. Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
                return false
            }

            if(!seenKeys.add(current.publicKeyNew)) {
                logger.warn("Validation failed. The chain revisits a key. Each key must appear only once. Repeated key: ${current.publicKeyNew.toStringShort()}, Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
                return false
            }
        }

        // Validate last proof
        // Check it ends with the expected current key
        // The validation of the last proof is already covered in the intermediate proofChain validation,
        // so we only need to check the current key matches
        if(!endsWithKey(currentKey)) {
            logger.warn("Validation failed. The chain does not end with the expected key. Expected: ${currentKey.toStringShort()}, Actual: ${this.currentKey.toStringShort()}, Original key: ${originalKey.toStringShort()}, Current key: ${currentKey.toStringShort()}")
            return false
        }

        return true
    }

    private fun startsWithKey(expectedOriginalKey: PublicKey): Boolean {
        return originalKey == expectedOriginalKey
    }

    private fun endsWithKey(expectedCurrentKey: PublicKey): Boolean {
        return currentKey == expectedCurrentKey
    }

    private fun isContinuous(previous: KeyRotationProof, current: KeyRotationProof): Boolean {
        return previous.publicKeyNew == current.publicKeyOld
    }

    private fun isValid(proof: KeyRotationProof): Boolean {
        val scheme: SignatureScheme = Crypto.findSignatureScheme(proof.publicKeyOld)
        return try {
            Crypto.doVerify(scheme, proof.publicKeyOld, proof.signature, proof.publicKeyNew.encoded)
            true
        } catch (e: GeneralSecurityException) {
            logger.warn("Invalid key rotation proof. Old key: ${proof.publicKeyOld.toStringShort()}, New key: ${proof.publicKeyNew.toStringShort()}, Message: ${e.message}")
            false
        }
    }
}
