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
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     */
    fun digest(bytes: ByteArray): ByteArray

    /**
     * Computes the digest of the [ByteArray] which is resistant to pre-image attacks.
     * Default implementation provides double hashing, but can it be changed to single hashing or something else for better performance.
     */
    fun preImageResistantDigest(bytes: ByteArray): ByteArray = digest(digest(bytes))

    /**
     * Computes the digest of the [ByteArray] which is resistant to pre-image attacks.
     * Default implementation provides double hashing, but can it be changed to single hashing or something else for better performance.
     */
    fun nonceDigest(bytes: ByteArray): ByteArray = digest(digest(bytes))

    /**
     * Computes the digest of the [ByteArray] which is resistant to pre-image attacks.
     * Default implementation provides double hashing, but can it be changed to single hashing or something else for better performance.
     */
    fun componentDigest(bytes: ByteArray): ByteArray = digest(digest(bytes))
}
