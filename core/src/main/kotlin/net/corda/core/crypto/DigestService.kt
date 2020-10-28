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
class DigestService private constructor(val digestLength: Int,
                                        val hashAlgorithm: String,
                                        val hashTwiceNonce : Boolean = true,
                                        val hashTwiceComponent : Boolean = true) {

    init {
        require(hashAlgorithm.isNotEmpty()) { "Hash algorithm name unavailable or not specified" }
        require((hashTwiceNonce && hashTwiceComponent) || hashAlgorithm != SHA2_256) {
            "SHA2-256 requires doubleHashNonce and doubleHashComponent to be set"
        }
    }

    @KeepForDJVM
    companion object {
        private const val NONCE_SIZE = 8
        private const val WORD_SIZE_32 = 32
        // The `default` instance will be configured from the network parameters. For now hardcoded to SHA2_256.
        //val default : DigestService by lazy { DigestService(WORD_SIZE_32, SHA2_256) }
        val default : DigestService by lazy { sha3_256 }
        val sha2_256: DigestService by lazy { DigestService(WORD_SIZE_32, SHA2_256, hashTwiceNonce = true, hashTwiceComponent = true) }
        val sha3_256: DigestService by lazy { DigestService(WORD_SIZE_32, SHA3_256, hashTwiceNonce = false, hashTwiceComponent = false) }

        fun create(hashAlgorithm: String) =
            create(SecureHash.digestLengthFor(hashAlgorithm), hashAlgorithm, hashTwiceNonce = true, hashTwiceComponent = true)

        fun create(digestLength: Int, hashAlgorithm: String) =
                create(digestLength, hashAlgorithm, hashTwiceNonce = true, hashTwiceComponent = true)

        fun create(hashAlgorithm: String, hashTwiceNonce : Boolean, hashTwiceComponent : Boolean) =
            create(SecureHash.digestLengthFor(hashAlgorithm), hashAlgorithm, hashTwiceNonce, hashTwiceComponent)

        fun create(digestLength: Int, hashAlgorithm: String, hashTwiceNonce : Boolean, hashTwiceComponent : Boolean) =
                DigestService(digestLength, hashAlgorithm, hashTwiceNonce, hashTwiceComponent)
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
     * Generates a random SHA-256 value.
     */
    //fun randomHash(): SecureHash = SecureHash.random(hashAlgorithm)

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
        val data = salted(privacySalt.bytes, ByteBuffer.allocate(NONCE_SIZE).putInt(groupIndex).putInt(internalIndex).array())
        return if(hashTwiceNonce) SecureHash.hashTwiceAs(hashAlgorithm, data) else SecureHash.hashAs(hashAlgorithm, data)
    }
}
