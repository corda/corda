package net.corda.core.crypto

import net.corda.core.DeleteForDJVM
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import org.bouncycastle.crypto.digests.Blake2sDigest
import org.bouncycastle.jcajce.provider.digest.Blake2b

@CordaSerializable
interface DigestService {
    /**
     * The length of the digest in bytes
     */
    val digestLength: Int

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     */
    fun hash(bytes: ByteArray): SecureHash

    /**
     * Computes the digest of the [String]'s UTF-8 byte contents.
     *
     * @param str [String] whose UTF-8 contents will be hashed.
     */
    fun hash(str: String): SecureHash

    /**
     * A digest value consisting of [digestLength] 0xFF bytes.
     *
     * TODO: These seem to be also mostly used for testing? I see only two other places?
     */
    val allOnesHash: SecureHash

    /**
     * A hash value consisting of [digestLength] 0x00 bytes.
     */
    val zeroHash: SecureHash
}

@CordaSerializable
object SHA256DigestService : DigestService {
    override val digestLength = 32
    override fun hash(bytes: ByteArray) = SecureHash.sha256(bytes)
    override fun hash(str: String): SecureHash = hash(str.toByteArray())
    override val allOnesHash = SecureHash.allOnesHash
    override val zeroHash = SecureHash.zeroHash
}

@CordaSerializable
object SHA256dDigestService : DigestService {
    override val digestLength = 32
    override fun hash(bytes: ByteArray) = SecureHash.sha256Twice(bytes)
    override fun hash(str: String): SecureHash = hash(str.toByteArray())
    override val allOnesHash = SecureHash.allOnesHash
    override val zeroHash = SecureHash.zeroHash
}

@CordaSerializable
object BLAKE2s256DigestService : DigestService {
    private val blake2s256 = Blake2sDigest(null, 32, null, "12345678".toByteArray())
    override val digestLength: Int by lazy { blake2s256.digestSize }

    /**
     * BLAKE2s256 is resistant to length extension attack, so no double hashing needed.
     */
    override fun hash(bytes: ByteArray): SecureHash {
        blake2s256.reset()
        blake2s256.update(bytes, 0, bytes.size)
        val hash = ByteArray(32)
        blake2s256.doFinal(hash, 0)
        return SecureHash.BLAKE2s256(hash)
    }

    override fun hash(str: String): SecureHash = hash(str.toByteArray())
    override val allOnesHash = SecureHash.BLAKE2s256(ByteArray(digestLength) { 255.toByte() })
    override val zeroHash = SecureHash.BLAKE2s256(ByteArray(digestLength) { 0.toByte() })
}

@CordaSerializable
object BLAKE2b256DigestService : DigestService {
    private val blake2b256 = Blake2b.Blake2b256()
    override val digestLength: Int by lazy { blake2b256.digestLength }

    /**
     * BLAKE2b256 is resistant to length extension attack, so no double hashing needed.
     */
    override fun hash(bytes: ByteArray) = SecureHash.BLAKE2b256(blake2b256.digest(bytes))
    override fun hash(str: String): SecureHash = hash(str.toByteArray())
    override val allOnesHash = SecureHash.BLAKE2b256(ByteArray(digestLength) { 255.toByte() })
    override val zeroHash = SecureHash.BLAKE2b256(ByteArray(digestLength) { 0.toByte() })
}

/**
 * For testing only
 */
@DeleteForDJVM
fun SHA256dDigestService.random() = SecureHash.randomSHA256()

@DeleteForDJVM
fun BLAKE2b256DigestService.random() = SecureHash.BLAKE2b256(secureRandomBytes(32))

@DeleteForDJVM
fun BLAKE2s256DigestService.random() = SecureHash.BLAKE2s256(secureRandomBytes(32))


