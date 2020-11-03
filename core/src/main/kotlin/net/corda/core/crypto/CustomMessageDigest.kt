package net.corda.core.crypto

import net.corda.core.KeepForDJVM

/**
 * Interface for injecting custom digest implementation bypassing JCA.
 */
@KeepForDJVM
interface CustomMessageDigest {
    /**
     * The length of the digest in bytes
     */
    val digestLength: Int

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     */
    fun digest(bytes: ByteArray): ByteArray
}
