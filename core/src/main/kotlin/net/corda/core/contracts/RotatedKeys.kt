package net.corda.core.contracts

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.hash
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

object CordaRotatedKeys {
    val keys = RotatedKeys()
}

/**
 * This class represents the rotated keys known by this node. A public key in this class is identified by its SHA-256 hash of the public key. A
 * single rotated key is identified by an ordered list of 2 or more hashes. An entry in the list means the key has been rotated to the next
 * entry in the list. For the purposes of SignatureConstraints this means we treat all entries in the list as equivalent.
 * A single rotated key is a list of hashes. To determine if two hashes come from the same rotated list, we first retrieve the last entry in
 * list the hash is part of. This is done for both hashes. Then if the last entry is the same for both hashes you know the hashes came from
 * the same list.
 * This class supports multiple rotated keys which can be configured.
 * @param rotatedSigningKeys A List of rotated keys. With a rotated key being represented by a list of hashes. This list comes from
 * node.conf.
 *
 */
@CordaSerializable
data class RotatedKeys(val rotatedSigningKeys: List<List<SecureHash>> = emptyList()): SingletonSerializeAsToken() {
    private val canBeTransitionedMap: ConcurrentHashMap<Pair<PublicKey, PublicKey>, Boolean> = ConcurrentHashMap()
    private val rotateMap: ConcurrentHashMap<SecureHash, SecureHash> = ConcurrentHashMap()

    init {
        rotatedSigningKeys.forEach { rotatedKeyList ->
            rotatedKeyList.forEach { key ->
                rotateMap[key] = rotatedKeyList.last()
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
            rotate(inputKey.hash) == rotate(outputKey.hash) -> true
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