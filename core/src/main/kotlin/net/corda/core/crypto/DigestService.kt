package net.corda.core.crypto

import net.corda.core.KeepForDJVM
import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.SecureHash.Companion.SHA2_256
import net.corda.core.crypto.SecureHash.Companion.SHA3_256
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import java.nio.ByteBuffer

@CordaSerializable
@KeepForDJVM
open class DigestService(val digestLength: Int,
                         val hashAlgorithm: String,
                         val hashTwiceNonce : Boolean = true,
                         val hashTwiceComponent : Boolean = true) {

    constructor() : this (SHA2_256)

    constructor(hashAlgorithm: String) : this(SecureHash.digestLengthFor(hashAlgorithm), hashAlgorithm)

    constructor(hashAlgorithm: String, doubleHashNonce: Boolean, doubleHashComponent: Boolean)
            : this(SecureHash.digestLengthFor(hashAlgorithm), hashAlgorithm, doubleHashNonce, doubleHashComponent)

    init {
        require(hashAlgorithm.isNotEmpty()) { "Hash algorithm name unavailable or not specified" }
        require((hashTwiceNonce && hashTwiceComponent) || hashAlgorithm != SHA2_256) {
            "SHA2-256 requires doubleHashNonce and doubleHashComponent to be set"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is DigestService &&
                other.digestLength == this.digestLength &&
                other.hashAlgorithm == this.hashAlgorithm
    }

    override fun hashCode(): Int = digestLength * 31 + hashAlgorithm.hashCode() * 31

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     * @param salt The [ByteArray] to use as salt.
     */
    fun hash(bytes: ByteArray): SecureHash = SecureHash.hashAs(hashAlgorithm, bytes)

    /**
     * Computes the digest of the [String]'s UTF-8 byte contents.
     *
     * @param str [String] whose UTF-8 contents will be hashed.
     * @param salt The [String] to use as salt.
     */
    fun hash(str: String): SecureHash = hash(str.toByteArray())

    /**
     * A digest value consisting of [digestLength] 0xFF bytes.
     */
    val allOnesHash: SecureHash
        get() = SecureHash.allOnesHashFor(hashAlgorithm)

    /**
     * A hash value consisting of [digestLength] 0x00 bytes.
     */
    val zeroHash: SecureHash
        get() = SecureHash.zeroHashFor(hashAlgorithm)

    fun salted(salt: ByteArray?, data: ByteArray) = salt?.plus(data) ?: data

    /**
     * Compute the hash of each serialised component so as to be used as Merkle tree leaf. The resultant output (leaf) is
     * calculated using the service's hash algorithm, thus HASH(HASH(nonce || serializedComponent)) if doubleHashComponent is or
     * HASH(nonce || serializedComponent) otherwise, where nonce is computed from [computeNonce].
     */
    fun componentHash(opaqueBytes: OpaqueBytes, privacySalt: PrivacySalt, componentGroupIndex: Int, internalIndex: Int): SecureHash =
            componentHash(computeNonce(privacySalt, componentGroupIndex, internalIndex), opaqueBytes)

    /** Return the HASH(HASH(nonce || serializedComponent)) if doubleHashComponent is set, HASH(nonce || serializedComponent) otherwise */
    fun componentHash(nonce: SecureHash, opaqueBytes: OpaqueBytes): SecureHash {
        val data = nonce.bytes + opaqueBytes.bytes
        return if(hashTwiceComponent) SecureHash.hashTwiceAs(hashAlgorithm, data) else SecureHash.hashAs(hashAlgorithm, data)
    }

    /**
     * Serialise the object and return the hash of the serialized bytes. Note that the resulting hash may not be deterministic
     * across platform versions: serialization can produce different values if any of the types being serialized have changed,
     * or if the version of serialization specified by the context changes.
     */
    fun <T : Any> serializedHash(x: T): SecureHash =
            SecureHash.hashAs(hashAlgorithm, x.serialize(context = SerializationDefaults.P2P_CONTEXT.withoutReferences()).bytes)

    /**
     * Method to compute a nonce based on privacySalt, component group index and component internal index.
     * SHA256d (double SHA256) is used to prevent length extension attacks.
     * @param privacySalt a [PrivacySalt].
     * @param groupIndex the fixed index (ordinal) of this component group.
     * @param internalIndex the internal index of this object in its corresponding components list.
     * @return HASH(HASH(privacySalt || groupIndex || internalIndex)) if doubleHashNonce is set,
     *         HASH(privacySalt || groupIndex || internalIndex) otherwise
     */
    fun computeNonce(privacySalt: PrivacySalt, groupIndex: Int, internalIndex: Int) : SecureHash {
        val data = salted(privacySalt.bytes, ByteBuffer.allocate(8).putInt(groupIndex).putInt(internalIndex).array())
        return if(hashTwiceNonce) SecureHash.hashTwiceAs(hashAlgorithm, data) else SecureHash.hashAs(hashAlgorithm, data)
    }
}

@CordaSerializable
@KeepForDJVM
class SHA2256DigestService : DigestService(32, SHA2_256)

@CordaSerializable
@KeepForDJVM
class SHA3256DigestService : DigestService(32, SHA3_256)

@KeepForDJVM
object DefaultDigest {
    // IEE TODO: shall be initialized with network parameters to determine the default network hash algorithm
    val instance : DigestService by lazy { SHA2256DigestService() }
    val sha256 : DigestService = SHA2256DigestService()
}