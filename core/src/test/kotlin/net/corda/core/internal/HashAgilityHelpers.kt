package net.corda.core.internal

import net.corda.core.crypto.DigestAlgorithm
import net.corda.core.crypto.SecureHash
import org.bouncycastle.crypto.digests.Blake2sDigest

/**
 * A set of custom hash algorithms
 */

open class BLAKE2s256DigestAlgorithm : DigestAlgorithm {
    override val algorithm = "BLAKE_TEST"

    override val digestLength = 32

    protected fun blake2sHash(bytes: ByteArray): ByteArray {
        val blake2s256 = Blake2sDigest(null, digestLength, null, "12345678".toByteArray())
        blake2s256.reset()
        blake2s256.update(bytes, 0, bytes.size)
        val hash = ByteArray(digestLength)
        blake2s256.doFinal(hash, 0)
        return hash
    }

    override fun digest(bytes: ByteArray): ByteArray = blake2sHash(bytes)
}

class SHA256BLAKE2s256DigestAlgorithm : BLAKE2s256DigestAlgorithm() {
    override val algorithm = "SHA256-BLAKE2S256-TEST"

    override fun digest(bytes: ByteArray): ByteArray = SecureHash.hashAs(SecureHash.SHA2_256, bytes).bytes

    override fun componentDigest(bytes: ByteArray): ByteArray = blake2sHash(bytes)

    override fun nonceDigest(bytes: ByteArray): ByteArray = blake2sHash(bytes)
}