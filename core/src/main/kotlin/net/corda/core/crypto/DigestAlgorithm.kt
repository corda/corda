package net.corda.core.crypto

import net.corda.core.KeepForDJVM

/**
 * Interface for injecting custom digest implementation bypassing JCA.
 */
@KeepForDJVM
interface DigestAlgorithm {
    /**
     * Algorithm identifier.
     */
    val algorithm: String

    /**
     * The length of the digest in bytes.
     */
    val digestLength: Int

    /**
     * ENT-6225 raises a question in relation to zero hash padding in the case of a single leaf in a component group.
     * No padding for the single leaf case becomes an issue when a transaction has a mixed condition of multiple and
     * single components' component groups, and a hybrid digest algorithm is used, resulting in component groups roots
     * hashed with different hash functions.
     * A hybrid digest algorithm is a [DigestAlgorithm] implementation where [componentDigest] and [nonceDigest] hash
     * function differs from the [digest] hash function.
     */
    val isHybrid: Boolean get() = false

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     */
    fun digest(bytes: ByteArray): ByteArray

    /**
     * Computes the digest of the [ByteArray] which is resistant to pre-image attacks. Only used to calculate the hash of the leaves of the
     * ComponentGroup Merkle tree, starting from its serialized components.
     * Default implementation provides double hashing, but can it be changed to single hashing or something else for better performance.
     */
    fun componentDigest(bytes: ByteArray): ByteArray = digest(digest(bytes))

    /**
     * Computes the digest of the [ByteArray] which is resistant to pre-image attacks. Only used to calculate the nonces for the leaves of
     * the ComponentGroup Merkle tree.
     * Default implementation provides double hashing, but can it be changed to single hashing or something else for better performance.
     */
    fun nonceDigest(bytes: ByteArray): ByteArray = digest(digest(bytes))
}
