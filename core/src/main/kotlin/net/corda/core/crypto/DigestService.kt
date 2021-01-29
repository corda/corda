package net.corda.core.crypto

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.PrivacySalt
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The DigestService class is a service that offers the main crypto methods for calculating transaction hashes and
 * building Merkle trees. The [default] instance is passed by default to instances of classes like TransactionBuilder
 * and as a parameter to MerkleTree.getMerkleTree(...) method. In future the [default] instance can be parametrized
 * to initialize with the network default hash algorithm or just a more secure algorithm (e.g. SHA3_256). While the
 * SHA2_256 is vulnerable to pre-image attacks, the computeNonce and componentHash methods behaviour is defined by
 * the hashTwiceNonce and hashTwiceComponent; with SHA2_256 they both must be set to true to ensure pre-image attack
 * won't work (and for backward compatibility), but for other algorithms like SHA3_256 that are not affected, they
 * can and should be set to false as hashing twice would not improve security but affect performance.
 *
 * @param hashAlgorithm the name of the hash algorithm to be used for the instance
 */
@CordaSerializable
@KeepForDJVM
data class DigestService(val hashAlgorithm: String) {
    init {
        require(hashAlgorithm.isNotEmpty()) { "Hash algorithm name unavailable or not specified" }
    }

    @KeepForDJVM
    companion object {
        private const val NONCE_SIZE = 8
        /**
         * The [default] instance will be parametrized and initialized at runtime. It would be probably useful to assume an override
         * priority order.
         */
        val default : DigestService by lazy { sha2_256 }
        val sha2_256: DigestService by lazy { DigestService(SecureHash.SHA2_256) }
        val sha2_384: DigestService by lazy { DigestService(SecureHash.SHA2_384) }
        val sha2_512: DigestService by lazy { DigestService(SecureHash.SHA2_512) }
    }

    /**
     * Specifies the WORD size for the given hash algorithm.
     */
    val digestLength: Int
        get() = SecureHash.digestLengthFor(hashAlgorithm)

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     */
    fun hash(bytes: ByteArray): SecureHash = SecureHash.hashAs(hashAlgorithm, bytes)

    /**
     * Computes the digest of the [String]'s UTF-8 byte contents.
     *
     * @param str [String] whose UTF-8 contents will be hashed.
     */
    fun hash(str: String): SecureHash = hash(str.toByteArray())

    /**
     * A digest value consisting of 0xFF bytes.
     */
    val allOnesHash: SecureHash
        get() = SecureHash.allOnesHashFor(hashAlgorithm)

    /**
     * A hash value consisting of 0x00 bytes.
     */
    val zeroHash: SecureHash
        get() = SecureHash.zeroHashFor(hashAlgorithm)

//    val privacySalt: PrivacySalt
//        get() = PrivacySalt.createFor(hashAlgorithm)

    /**
     * Compute the hash of each serialised component so as to be used as Merkle tree leaf. The resultant output (leaf) is
     * calculated using the service's hash algorithm, thus HASH(HASH(nonce || serializedComponent)) for SHA2-256 and other
     * algorithms loaded via JCA [MessageDigest], or DigestAlgorithm.preImageResistantDigest(nonce || serializedComponent)
     * otherwise, where nonce is computed from [computeNonce].
     */
    fun componentHash(opaqueBytes: OpaqueBytes, privacySalt: PrivacySalt, componentGroupIndex: Int, internalIndex: Int): SecureHash =
            componentHash(computeNonce(privacySalt, componentGroupIndex, internalIndex), opaqueBytes)

    /** Return the HASH(HASH(nonce || serializedComponent)) for SHA2-256 and other algorithms loaded via JCA [MessageDigest],
     *  otherwise it's defined by DigestAlgorithm.preImageResistantDigest(nonce || serializedComponent). */
    fun componentHash(nonce: SecureHash, opaqueBytes: OpaqueBytes): SecureHash {
        val data = nonce.bytes + opaqueBytes.bytes
        return SecureHash.componentHashAs(hashAlgorithm, data)
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
     * @return HASH(HASH(privacySalt || groupIndex || internalIndex)) for SHA2-256 and other algorithms loaded via JCA [MessageDigest],
     *         otherwise it's defined by DigestAlgorithm.preImageResistantDigest(privacySalt || groupIndex || internalIndex).
     */
    fun computeNonce(privacySalt: PrivacySalt, groupIndex: Int, internalIndex: Int) : SecureHash {
        val data = (privacySalt.bytes + ByteBuffer.allocate(NONCE_SIZE).putInt(groupIndex).putInt(internalIndex).array())
        return SecureHash.nonceHashAs(hashAlgorithm, data)
    }
}

@DeleteForDJVM
fun DigestService.randomHash(): SecureHash = SecureHash.random(this.hashAlgorithm)
