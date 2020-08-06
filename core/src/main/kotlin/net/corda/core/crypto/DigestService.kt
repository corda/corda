package net.corda.core.crypto

import com.ing.dlt.zkkrypto.ecc.pedersenhash.PedersenHash
import com.ing.dlt.zkkrypto.util.BitArray
import net.corda.core.DeleteForDJVM
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.crypto.digests.Blake2sDigest

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
     * @param salt The [ByteArray] to use as salt.
     */
    fun hash(bytes: ByteArray, salt: ByteArray? = null): SecureHash

    /**
     * Computes the digest of the [String]'s UTF-8 byte contents.
     *
     * @param str [String] whose UTF-8 contents will be hashed.
     * @param salt The [String] to use as salt.
     */
    fun hash(str: String, salt: String? = null): SecureHash

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

    fun salted(msg: ByteArray, salt: ByteArray?): ByteArray {
        return salt?.plus(msg) ?: msg
    }
}

@CordaSerializable
object SHA256DigestService : DigestService {
    override val digestLength = 32
    override fun hash(bytes: ByteArray, salt: ByteArray?): SecureHash = SecureHash.sha256(salted(bytes, salt))
    override fun hash(str: String, salt: String?): SecureHash = hash(str.toByteArray(), salt?.toByteArray())
    override val allOnesHash = SecureHash.allOnesHash
    override val zeroHash = SecureHash.zeroHash
}

@CordaSerializable
object SHA256dDigestService : DigestService {
    override val digestLength = 32
    override fun hash(bytes: ByteArray, salt: ByteArray?): SecureHash = SecureHash.sha256Twice(salted(bytes, salt))
    override fun hash(str: String, salt: String?): SecureHash = hash(str.toByteArray(), salt?.toByteArray())
    override val allOnesHash = SecureHash.allOnesHash
    override val zeroHash = SecureHash.zeroHash
}

@CordaSerializable
object BLAKE2s256DigestService : DigestService {
    override val digestLength = 32 // We explicitly set the length of Blake2s to 32 bytes, as it is configurable.

    /**
     * BLAKE2s256 is resistant to length extension attack, so no double hashing needed.
     */
    override fun hash(bytes: ByteArray, salt: ByteArray?): SecureHash {
        val blake2s256 = Blake2sDigest(null, digestLength, salt, "12345678".toByteArray())
        blake2s256.reset()
        blake2s256.update(bytes, 0, bytes.size)
        val hash = ByteArray(digestLength)
        blake2s256.doFinal(hash, 0)
        return SecureHash.BLAKE2s256(hash)
    }

    override fun hash(str: String, salt: String?): SecureHash = hash(str.toByteArray(), salt?.toByteArray())
    override val allOnesHash = SecureHash.BLAKE2s256(ByteArray(digestLength) { 255.toByte() })
    override val zeroHash = SecureHash.BLAKE2s256(ByteArray(digestLength) { 0.toByte() })
}

@CordaSerializable
object PedersenDigestService : DigestService {
    private val pedersen = PedersenHash.zinc()
    override val digestLength: Int by lazy { pedersen.hashLength }
    override fun hash(bytes: ByteArray, salt: ByteArray?): SecureHash {
        val saltBits = if (salt != null) BitArray(salt) else null
        return SecureHash.Pedersen(pedersen.hash(bytes, saltBits))
    }
    override fun hash(str: String, salt: String?): SecureHash = hash(str.toByteArray(), salt?.toByteArray())
    override val allOnesHash = SecureHash.Pedersen(ByteArray(digestLength) { 255.toByte() })
    override val zeroHash = SecureHash.Pedersen(ByteArray(digestLength) { 0.toByte() })
}

/**
 * For testing only
 */
@DeleteForDJVM
fun SHA256dDigestService.random() = SecureHash.randomSHA256()

@DeleteForDJVM
fun BLAKE2s256DigestService.random() = SecureHash.BLAKE2s256(secureRandomBytes(32))


