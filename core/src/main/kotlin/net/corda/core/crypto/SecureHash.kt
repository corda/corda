package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.parseAsHex
import net.corda.core.utilities.toHexString
import java.security.MessageDigest
import kotlin.reflect.KClass

/**
 * Container for a cryptographically secure hash value.
 * Provides utilities for generating a cryptographic hash using different algorithms.
 * Currently, the following cryptographic hash algorithms are supported:
 * <p><ul>
 * <li>SHA-256, member of the SHA-2 cryptographic hash functions with an output of 32 bytes. This is the default algorithm
 * for Merkle tree generation.
 * <li>SHA-512, member of the SHA-2 cryptographic hash functions with an output of 64 bytes.
 * </ul></p>
 */
@CordaSerializable
sealed class SecureHash(bytes: ByteArray) : OpaqueBytes(bytes) {

    /** SHA-256 is part of the SHA-2 hash function family. Generated hash is fixed size, 256-bits (32-bytes). */
    class SHA256(bytes: ByteArray) : SecureHash(bytes) {
        init {
            require(bytes.size == 32)
        }
    }

    /** SHA-512 is part of the SHA-2 hash function family. Generated hash is fixed size, 512-bits (64-bytes). */
    class SHA512(bytes: ByteArray) : SecureHash(bytes) {
        init {
            require(bytes.size == 64)
        }
    }

    override fun toString(): String = bytes.toHexString()

    /** The first 6 characters of the hash HEX String. */
    fun prefixChars(prefixLen: Int = 6) = toString().substring(0, prefixLen)

    /**
     * Concatenate the bytes of this object with the bytes of another [SecureHash] (of the same type)
     * and return the hash of the concatenated result.
     */
    fun hashConcat(other: SecureHash): SecureHash {
        return if (this::class == other::class) {
            SecureHash.hash(this.bytes + other.bytes, this::class)
        } else throw IllegalArgumentException("You can only hashConcat SecureHash objects of the same type.")
    }

    companion object {
        /** Properties of the [SHA256] algorithm. */
        val SHA256_ALGORITHM = SecureHashAlgorithm(
                "SHA-256",
                32,
                SHA256(ByteArray(32, { 0.toByte() })),
                SHA256(ByteArray(32, { 255.toByte() }))
        )

        /** Properties of the [SHA512] algorithm. */
        val SHA512_ALGORITHM = SecureHashAlgorithm(
                "SHA-512",
                64,
                SHA512(ByteArray(64, { 0.toByte() })),
                SHA512(ByteArray(64, { 255.toByte() }))
        )

        /** SHA256 is currently the default [SecureHash] algorithm. */
        val DEFAULT_ALGORITHM = SHA256_ALGORITHM

        /**
         * Method that receives a HEX [String] which represents a hash output and returns its corresponding [SecureHash]
         * object. Selection of the Hash algorithm is based on the input String size and if there are more than one hash
         * functions offering the same hash output size, then the default per size is selected.
         */
        @JvmStatic
        fun parse(str: String) = str.toUpperCase().parseAsHex().let {
            when (it.size) {
                32 -> SHA256(it)
                64 -> SHA512(it) // TODO: define default 64-bit SecureHash function when SHA3-512 is introduced.
                else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 32 or 64 bytes in hex: $str")
            }
        }

        /**
         * Method that receives a HEX [String] which represents a hash output and returns its corresponding [SecureHash]
         * object. The hash secureHashAlgorithm is defined by the input [secureHashAlgorithm].
         */
        @JvmStatic
        fun parse(str: String, secureHashAlgorithm: SecureHashAlgorithm = DEFAULT_ALGORITHM) = str.toUpperCase().parseAsHex().let {
            when(secureHashAlgorithm) {
                SHA256_ALGORITHM -> SHA256(it)
                SHA512_ALGORITHM -> SHA512(it)
                else -> throw IllegalArgumentException("Hash secureHashAlgorithm $secureHashAlgorithm is not supported")
            }
        }

        /**
         * Returns the corresponding [SecureHash] object by hashing the input [bytes], using the provided [SecureHashAlgorithm].
         * If the algorithm is not provided, then the [DEFAULT_ALGORITHM] is used.
         */
        @JvmStatic
        fun hash(bytes: ByteArray, secureHashAlgorithm: SecureHashAlgorithm = DEFAULT_ALGORITHM): SecureHash =
            when(secureHashAlgorithm) {
                SHA256_ALGORITHM -> SHA256(MessageDigest.getInstance(secureHashAlgorithm.algorithmName).digest(bytes))
                SHA512_ALGORITHM -> SHA512(MessageDigest.getInstance(secureHashAlgorithm.algorithmName).digest(bytes))
                else -> throw IllegalArgumentException("Hash secureHashAlgorithm $secureHashAlgorithm is not supported")
            }

        // Overloaded hash method, required for hashConcat.
        private fun hash(bytes: ByteArray, clazz: KClass<out SecureHash>): SecureHash =
            when(clazz) {
                SHA256::class -> hash(bytes, SHA256_ALGORITHM)
                SHA512::class -> hash(bytes, SHA512_ALGORITHM)
                else -> throw IllegalArgumentException("${clazz.simpleName} is not a supported SecureHash type")
            }

        /**
         * Returns the corresponding [SecureHash] object by double-hashing the input bytes, thus hash(hash(bytes)),
         * using the provided [SecureHashAlgorithm]. If the algorithm is not provided, then the [DEFAULT_ALGORITHM] is used.
         */
        @JvmStatic
        fun hashTwice(bytes: ByteArray, secureHashAlgorithm: SecureHashAlgorithm = DEFAULT_ALGORITHM): SecureHash =
                hash(hash(bytes, secureHashAlgorithm).bytes, secureHashAlgorithm)

        /**
         * Returns a random [SecureHash] object using the provided [SecureHashAlgorithm]. If the algorithm
         * is not provided, then the [DEFAULT_ALGORITHM] is used.
         */
        @JvmStatic
        fun randomHash(secureHashAlgorithm: SecureHashAlgorithm = DEFAULT_ALGORITHM): SecureHash =
                hash(newSecureRandom().generateSeed(secureHashAlgorithm.outputSize), secureHashAlgorithm)

        /** Computes the hash output of the input [bytes] and returns the corresponding [SHA256] object. */
        @JvmStatic fun sha256(bytes: ByteArray) = hash(bytes, SHA256_ALGORITHM) as SHA256
        /** Computes the double-hash output of the input [bytes], thus sha256(sha256(bytes)), and returns the corresponding [SHA256] object. */
        @JvmStatic fun sha256Twice(bytes: ByteArray) = hashTwice(bytes, SHA256_ALGORITHM) as SHA256

        /** Computes the hash output of the input [String]'s bytes and returns the corresponding [SHA256] object. */
        @JvmStatic fun sha256(str: String) = hash(str.toByteArray(), SHA256_ALGORITHM) as SHA256
        /** Returns a random [SHA256] object. */
        @JvmStatic fun randomSHA256() = randomHash(SHA256_ALGORITHM) as SHA256

        /** ZeroHash of the [DEFAULT_ALGORITHM]. */
        val zeroHash = DEFAULT_ALGORITHM.zeroHash
        /** AllOnesHash of the [DEFAULT_ALGORITHM]. */
        val allOnesHash = DEFAULT_ALGORITHM.allOnesHash
    }
}

/**
 * This class is used to define properties a [SecureHash] algorithm.
 * @property algorithmName the [String] name of the hash algorithm (eg. SHA-256, SHA-512).
 * @property outputSize the output size in bytes of this hash algorithm (eg. 32 for SHA-256, 64 for SHA-512).
 * @property zeroHash a flag-object used as a "padding" leaf to ensure hash-trees are binary trees. By convention, this is a [SecureHash] object with all bytes set to zero.
 * @property allOnesHash a flag-object used as a root of an empty Merkle tree (without leaves). By convention, this is a [SecureHash] object with all bits set to one.
 */
data class SecureHashAlgorithm(val algorithmName: String, val outputSize: Int, val zeroHash: SecureHash, val allOnesHash: SecureHash)

/** Compute the hash of this [ByteArray] using the [SecureHash.DEFAULT_ALGORITHM] and return its corresponding [SecureHash] object. */
fun ByteArray.hash(): SecureHash = SecureHash.hash(this, SecureHash.DEFAULT_ALGORITHM)
/** Compute the hash of this [OpaqueBytes] object using the [SecureHash.DEFAULT_ALGORITHM] and return its corresponding [SecureHash] object. */
fun OpaqueBytes.hash(): SecureHash = this.bytes.hash()

/** Compute the hash of this [ByteArray] using the [SecureHash.SHA256_ALGORITHM] and return its corresponding [SecureHash.SHA256] object. */
fun ByteArray.sha256(): SecureHash.SHA256 = SecureHash.sha256(this)
/** Compute the hash of this [OpaqueBytes] object using the [SecureHash.SHA256_ALGORITHM] and return its corresponding [SecureHash.SHA256] object. */
fun OpaqueBytes.sha256(): SecureHash.SHA256 = this.bytes.sha256()
