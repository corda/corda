package net.corda.core.contracts

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.hash
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object CordaRotatedKeys {
    val keys = RotatedKeys()
}

// The current development CorDapp code signing public key hash
const val DEV_CORDAPP_CODE_SIGNING_STR = "AA59D829F2CA8FDDF5ABEA40D815F937E3E54E572B65B93B5C216AE6594E7D6B"
// The non production CorDapp code signing public key hash
const val NEW_NON_PROD_CORDAPP_CODE_SIGNING_STR = "B710A80780A12C52DF8A0B4C2247E08907CCA5D0F19AB1E266FE7BAEA9036790"
// The production CorDapp code signing public key hash
const val PROD_CORDAPP_CODE_SIGNING_STR = "EB4989E7F861FEBEC242E6C24CF0B51C41E108D2C4479D296C5570CB8DAD3EE0"
// The new production CorDapp code signing public key hash
const val NEW_PROD_CORDAPP_CODE_SIGNING_STR = "01EFA14B42700794292382C1EEAC9788A26DAFBCCC98992C01D5BC30EEAACD28"

// Rotations used by Corda
private val CORDA_SIGNING_KEY_ROTATIONS = listOf(
    listOf(SecureHash.create(DEV_CORDAPP_CODE_SIGNING_STR).sha256(), SecureHash.create(NEW_NON_PROD_CORDAPP_CODE_SIGNING_STR).sha256()),
    listOf(SecureHash.create(PROD_CORDAPP_CODE_SIGNING_STR).sha256(), SecureHash.create(NEW_PROD_CORDAPP_CODE_SIGNING_STR).sha256())
)

/**
 * This class represents the rotated CorDapp signing keys known by this node.
 *
 * A public key in this class is identified by its SHA-256 hash of the public key encoded bytes (@see PublicKey.getEncoded()).
 * A sequence of rotated keys is represented by a list of hashes of those public keys.  The list of those lists represents
 * each unrelated set of rotated keys.  A key should not appear more than once, either in the same list of in multiple lists.
 *
 * For the purposes of SignatureConstraints this means we treat all entries in a list of key hashes as equivalent.
 * For two keys to be equivalent, they must be equal, or they must appear in the same list of hashes.
 *
 * @param rotatedSigningKeys A List of rotated keys. With a rotated key being represented by a list of hashes. This list comes from
 * node.conf.
 *
 */
@CordaSerializable
data class RotatedKeys(val rotatedSigningKeys: List<List<SecureHash>> = emptyList()): SingletonSerializeAsToken() {
    private val canBeTransitionedMap: ConcurrentMap<Pair<PublicKey, PublicKey>, Boolean> = ConcurrentHashMap()
    private val rotateMap: Map<SecureHash, SecureHash> = HashMap<SecureHash, SecureHash>().apply {
        (rotatedSigningKeys + CORDA_SIGNING_KEY_ROTATIONS).forEach { rotatedKeyList ->
            rotatedKeyList.forEach { key ->
                if (this.containsKey(key)) throw IllegalStateException("The key with sha256(hash) $key appears in the rotated keys configuration more than once.")
                this[key] = rotatedKeyList.last()
            }
        }
    }

    fun canBeTransitioned(inputKey: PublicKey, outputKeys: List<PublicKey>): Boolean {
        return canBeTransitioned(inputKey, CompositeKey.Builder().addKeys(outputKeys).build())
    }

    fun canBeTransitioned(inputKeys: List<PublicKey>, outputKeys: List<PublicKey>): Boolean {
        return canBeTransitioned(CompositeKey.Builder().addKeys(inputKeys).build(), CompositeKey.Builder().addKeys(outputKeys).build())
    }

    fun canBeTransitioned(inputKey: PublicKey, outputKey: PublicKey): Boolean {
        // Need to handle if inputKey and outputKey are composite keys. They could be if part of SignatureConstraints
        return canBeTransitionedMap.getOrPut(Pair(inputKey, outputKey)) {
            when {
                (inputKey is CompositeKey && outputKey is CompositeKey) -> compareKeys(inputKey, outputKey)
                (inputKey is CompositeKey && outputKey !is CompositeKey) -> compareKeys(inputKey, outputKey)
                (inputKey !is CompositeKey && outputKey is CompositeKey) -> compareKeys(inputKey, outputKey)
                else -> isRotatedEquals(inputKey, outputKey)
            }
        }
    }

    private fun rotate(key: SecureHash): SecureHash {
        return rotateMap[key] ?: key
    }

    private fun isRotatedEquals(inputKey: PublicKey, outputKey: PublicKey): Boolean {
        return when {
            inputKey == outputKey -> true
            rotate(inputKey.hash.sha256()) == rotate(outputKey.hash.sha256()) -> true
            else -> false
        }
    }

    private fun compareKeys(inputKey: CompositeKey, outputKey: PublicKey): Boolean {
        if (inputKey.leafKeys.size == 1) {
            return canBeTransitioned(inputKey.leafKeys.first(), outputKey)
        }
        return false
    }

    private fun compareKeys(inputKey: PublicKey, outputKey: CompositeKey): Boolean {
        if (outputKey.leafKeys.size == 1) {
            return canBeTransitioned(inputKey, outputKey.leafKeys.first())
        }
        return false
    }

    private fun compareKeys(inputKey: CompositeKey, outputKey: CompositeKey): Boolean {
        if (inputKey.leafKeys.size != outputKey.leafKeys.size) {
            return false
        }
        else {
            inputKey.leafKeys.forEach { inputLeafKey ->
                if (!outputKey.leafKeys.any { outputLeafKey -> canBeTransitioned(inputLeafKey, outputLeafKey) }) {
                    return false
                }
            }
            return true
        }
    }
}