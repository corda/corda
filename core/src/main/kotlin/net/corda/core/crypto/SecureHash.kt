package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.parseAsHex
import net.corda.core.utilities.toHexString
import java.security.MessageDigest

/**
 * Container for a cryptographically secure hash value.
 * Provides utilities for generating a cryptographic hash using different algorithms (currently only SHA-256 supported).
 */
@CordaSerializable
sealed class SecureHash(bytes: ByteArray) : OpaqueBytes(bytes) {
    /** SHA-256 is part of the SHA-2 hash function family. Generated hash is fixed size, 256-bits (32-bytes). */
    class SHA256(bytes: ByteArray) : SecureHash(bytes) {
        init {
            require(bytes.size == 32)
        }
    }

    override fun toString(): String = bytes.toHexString()

    fun prefixChars(prefixLen: Int = 6) = toString().substring(0, prefixLen)
    fun hashConcat(other: SecureHash) = (this.bytes + other.bytes).sha256()

    // Like static methods in Java, except the 'companion' is a singleton that can have state.
    companion object {
        @JvmStatic
        fun parse(str: String) = str.toUpperCase().parseAsHex().let {
            when (it.size) {
                32 -> SHA256(it)
                else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 32 bytes in hex: $str")
            }
        }

        @JvmStatic
        fun sha256(bytes: ByteArray) = SHA256(MessageDigest.getInstance("SHA-256").digest(bytes))

        @JvmStatic
        fun sha256Twice(bytes: ByteArray) = sha256(sha256(bytes).bytes)

        @JvmStatic
        fun sha256(str: String) = sha256(str.toByteArray())

        @JvmStatic
        fun randomSHA256() = sha256(newSecureRandom().generateSeed(32))

        val zeroHash = SecureHash.SHA256(ByteArray(32, { 0.toByte() }))
        val allOnesHash = SecureHash.SHA256(ByteArray(32, { 255.toByte() }))
    }

    // In future, maybe SHA3, truncated hashes etc.
}

fun ByteArray.sha256(): SecureHash.SHA256 = SecureHash.sha256(this)
fun OpaqueBytes.sha256(): SecureHash.SHA256 = SecureHash.sha256(this.bytes)
