package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.serialization.CordaSerializable
import java.security.GeneralSecurityException
import java.security.PublicKey

@CordaSerializable
data class KeyRotationProofChain(private val proofChain: List<KeyRotationProof>) : Iterable<KeyRotationProof> {
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
    fun isValid(originalKey: PublicKey, currentKey: PublicKey): Boolean {
        if(originalKey == currentKey) {
            return true
        }
        
        if (isEmpty()) {
            return false
        }

        // Validate first proof
        if(!startsWithKey(originalKey) || !isValid(proofChain[0])){
            return false
        }

        // Validate intermediate proofChain (continuity + proof)
        proofChain.zipWithNext().forEach { (previous, current) ->
            if(!isContinuous(previous, current) || !isValid(current)) {
                return false
            }
        }

        // Validate last proof
        // Check it ends with the expected current key
        // The validation of the last proof is already covered in the intermediate proofChain validation,
        // so we only need to check the current key matches
        return endsWithKey(currentKey)
    }

    // Allow for-each style iteration
    override operator fun iterator(): Iterator<KeyRotationProof> = proofChain.iterator()

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
        } catch (_: GeneralSecurityException) {
            false
        }
    }
}
