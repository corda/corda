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

    fun isValid(rotatedToKey: PublicKey): Boolean {
        if(isEmpty()) {
            return true
        }

        return isValid(originalKey, rotatedToKey)
    }

    fun asList(): List<KeyRotationProof> = proofChain

    /**
     * Checks whether this proof chain proves a valid rotation lineage FROM [rotatedFromKey] TO [rotatedToKey].
     *
     * The chain is valid only if all of the following hold:
     * 1. It STARTS at [rotatedFromKey] - the chain's first proof.publicKeyOld must equal [rotatedFromKey].
     * 2. It ENDS at [rotatedToKey] - the chain's last proof.publicKeyNew must equal [rotatedToKey].
     * 3. Each proof is continuous with the next (previous.publicKeyNew == next.publicKeyOld).
     * 4. Each proof is cryptographically signed by its own publicKeyOld.
     * 5. No key appears more than once, so the lineage is acyclic.
     *
     * [rotatedFromKey] and [rotatedToKey] must be the exact endpoints: it is NOT enough for them to
     * appear somewhere inside the chain. A chain that merely contains them in the middle is rejected.
     *
     * @param rotatedFromKey the key the lineage must start from (matched against the chain's first publicKeyOld).
     * @param rotatedToKey the key the lineage must end at (matched against the chain's last publicKeyNew).
     */
    @Suppress("ComplexMethod")
    fun isValid(rotatedFromKey: PublicKey, rotatedToKey: PublicKey): Boolean {
        if (isEmpty()) {
            logger.warn("Validation failed. Chain is empty. Rotated-from key: ${rotatedFromKey.toStringShort()}, Rotated-to key: ${rotatedToKey.toStringShort()}")
            return false
        }

        // Validate first proof
        if(!startsWithKey(rotatedFromKey)){
            logger.warn("Validation failed. The chain does not start with the expected original key. Expected: ${rotatedFromKey.toStringShort()}, Actual: ${originalKey.toStringShort()}")
            return false
        }

        if(!isValid(proofChain[0])){
            logger.warn("Validation failed. Proof is invalid. Rotated-from key: ${rotatedFromKey.toStringShort()}, Rotated-to key: ${rotatedToKey.toStringShort()}")
            return false
        }

        // Track keys as the chain is processed: a key may appear only once, so a self-proof
        // (A -> A) or a loop (A -> B -> A) is rejected on the offending proof.
        val seenKeys = hashSetOf(proofChain[0].publicKeyOld)
        if(!seenKeys.add(proofChain[0].publicKeyNew)) {
            logger.warn("Validation failed. The chain revisits a key; each key must appear only once. Repeated key: ${proofChain[0].publicKeyNew.toStringShort()}, Rotated-from key: ${rotatedFromKey.toStringShort()}, Rotated-to key: ${rotatedToKey.toStringShort()}")
            return false
        }

        // Validate intermediate proofChain (continuity + proof)
        proofChain.zipWithNext().forEach { (previousProof, nextProof) ->
            if(!isContinuous(previousProof, nextProof)) {
                logger.warn("Validation failed. The chain is not continuous. The previous proof's new key must match the next proof's old key. Previous proof new key: ${previousProof.publicKeyNew.toStringShort()}, Next proof old key: ${nextProof.publicKeyOld.toStringShort()}, Rotated-from key: ${rotatedFromKey.toStringShort()}, Rotated-to key: ${rotatedToKey.toStringShort()}")
                return false
            }

            if(!isValid(nextProof)) {
                logger.warn("Validation failed. Proof is invalid. Rotated-from key: ${rotatedFromKey.toStringShort()}, Rotated-to key: ${rotatedToKey.toStringShort()}")
                return false
            }

            if(!seenKeys.add(nextProof.publicKeyNew)) {
                logger.warn("Validation failed. The chain revisits a key. Each key must appear only once. Repeated key: ${nextProof.publicKeyNew.toStringShort()}, Rotated-from key: ${rotatedFromKey.toStringShort()}, Rotated-to key: ${rotatedToKey.toStringShort()}")
                return false
            }
        }

        // Validate last proof
        // Check it ends with the expected current key
        // The validation of the last proof is already covered in the intermediate proofChain validation,
        // so we only need to check the current key matches
        if(!endsWithKey(rotatedToKey)) {
            logger.warn("Validation failed. The chain does not end with the expected key. Expected: ${rotatedToKey.toStringShort()}, Actual: ${currentKey.toStringShort()}")
            return false
        }

        return true
    }

    private fun startsWithKey(rotatedFromKey: PublicKey): Boolean {
        return originalKey == rotatedFromKey
    }

    private fun endsWithKey(rotatedToKey: PublicKey): Boolean {
        return currentKey == rotatedToKey
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
