package net.corda.core.contracts

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.hash
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

@CordaSerializable
data class RotatedKeysData(val rotatedSigningKeys: List<List<SecureHash>> = emptyList()) {
    private val canBeTransitionedMap: ConcurrentHashMap<Pair<PublicKey, PublicKey>, Boolean> = ConcurrentHashMap()
    private val rotateMap: ConcurrentHashMap<SecureHash, SecureHash> = ConcurrentHashMap()

    init {
        rotatedSigningKeys.forEach { rotatedKeyList ->
            rotatedKeyList.forEach { key ->
                rotateMap[key] = rotatedKeyList.last()
            }
        }
    }

    private fun rotate(key: SecureHash): SecureHash {
        return rotateMap[key] ?: key
    }

    private fun isRotated(inputKey: PublicKey, outputKey: PublicKey): Boolean {
        return when {
            inputKey == outputKey -> true
            rotate(inputKey.hash.sha256()) == rotate(outputKey.hash.sha256()) -> true
            else -> false
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
                else -> isRotated(inputKey, outputKey)
            }
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