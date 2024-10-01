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
        rotatedSigningKeys.forEach { rotatedKeyList ->
            rotatedKeyList.forEach { key ->
                if (this.containsKey(key)) throw IllegalStateException("The key with sha256(hash) $key appears in the rotated keys configuration more than once.")
                this[key] = rotatedKeyList.last()
            }
        }
        this[SecureHash.create("CA8986C9F49451C58724A8670E6E7E060528EA285DCAA18D8EE3DB8B9D9B6E79")] = SecureHash.create("7B5E1D11737CC84A17C350BFA2D9CCA38D34C238833EEC9F6082D5718B3B3CF9")
        this[SecureHash.create("7B5E1D11737CC84A17C350BFA2D9CCA38D34C238833EEC9F6082D5718B3B3CF9")] = SecureHash.create("7B5E1D11737CC84A17C350BFA2D9CCA38D34C238833EEC9F6082D5718B3B3CF9")
        this[SecureHash.create("6F6696296C3F58B55FB6CA865A025A3A6CC27AD17C4AFABA1E8EF062E0A82739")] = SecureHash.create("632AC4D0EF641E2FFCADBF2B7F315555F5D36107BB9A5338278FDB4A812D2360")
        this[SecureHash.create("632AC4D0EF641E2FFCADBF2B7F315555F5D36107BB9A5338278FDB4A812D2360")] = SecureHash.create("632AC4D0EF641E2FFCADBF2B7F315555F5D36107BB9A5338278FDB4A812D2360")
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