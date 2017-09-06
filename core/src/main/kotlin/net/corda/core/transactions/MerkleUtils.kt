@file:JvmName("MerkleUtils")
package net.corda.core.transactions

import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer

/**
 * If a privacy salt is provided, the resulted output (Merkle-leaf) is computed as
 * Hash(serializedObject || Hash(privacy_salt || obj_index_in_merkle_tree)).
 */
fun <T : Any> serializedHash(x: T, privacySalt: PrivacySalt?, index: Int): SecureHash {
    return if (privacySalt != null)
        serializedHash(x, computeNonce(privacySalt, index))
    else
        serializedHash(x)
}

fun <T : Any> serializedHash(x: T, nonce: SecureHash): SecureHash {
    return if (x !is PrivacySalt) // PrivacySalt is not required to have an accompanied nonce.
        (x.serialize(context = SerializationFactory.defaultFactory.defaultContext.withoutReferences()).bytes + nonce.bytes).sha256()
    else
        serializedHash(x)
}

fun <T : Any> serializedHash(x: T): SecureHash = x.serialize(context = SerializationFactory.defaultFactory.defaultContext.withoutReferences()).bytes.sha256()

/** The nonce is computed as Hash(privacySalt || index). */
fun computeNonce(privacySalt: PrivacySalt, index: Int) = (privacySalt.bytes + ByteBuffer.allocate(4).putInt(index).array()).sha256()
