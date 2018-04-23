/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import io.netty.util.concurrent.FastThreadLocal
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.parseAsHex
import net.corda.core.utilities.toHexString
import java.security.MessageDigest
import java.util.function.Supplier

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

    /**
     * Convert the hash value to an uppercase hexadecimal [String].
     */
    override fun toString(): String = bytes.toHexString()

    /**
     * Returns the first [prefixLen] hexadecimal digits of the [SecureHash] value.
     * @param prefixLen The number of characters in the prefix.
     */
    fun prefixChars(prefixLen: Int = 6) = toString().substring(0, prefixLen)

    /**
     * Append a second hash value to this hash value, and then compute the SHA-256 hash of the result.
     * @param other The hash to append to this one.
     */
    fun hashConcat(other: SecureHash) = (this.bytes + other.bytes).sha256()

    // Like static methods in Java, except the 'companion' is a singleton that can have state.
    companion object {
        /**
         * Converts a SHA-256 hash value represented as a hexadecimal [String] into a [SecureHash].
         * @param str A sequence of 64 hexadecimal digits that represents a SHA-256 hash value.
         * @throws IllegalArgumentException The input string does not contain 64 hexadecimal digits, or it contains incorrectly-encoded characters.
         */
        @JvmStatic
        fun parse(str: String): SHA256 {
            return str.toUpperCase().parseAsHex().let {
                when (it.size) {
                    32 -> SHA256(it)
                    else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 32 bytes in hex: $str")
                }
            }
        }

        private val sha256MessageDigest = SHA256DigestSupplier()

        /**
         * Computes the SHA-256 hash value of the [ByteArray].
         * @param bytes The [ByteArray] to hash.
         */
        @JvmStatic
        fun sha256(bytes: ByteArray) = SHA256(sha256MessageDigest.get().digest(bytes))

        /**
         * Computes the SHA-256 hash of the [ByteArray], and then computes the SHA-256 hash of the hash.
         * @param bytes The [ByteArray] to hash.
         */
        @JvmStatic
        fun sha256Twice(bytes: ByteArray) = sha256(sha256(bytes).bytes)

        /**
         * Computes the SHA-256 hash of the [String]'s UTF-8 byte contents.
         * @param str [String] whose UTF-8 contents will be hashed.
         */
        @JvmStatic
        fun sha256(str: String) = sha256(str.toByteArray())

        /**
         * Generates a random SHA-256 value.
         */
        @JvmStatic
        fun randomSHA256() = sha256(secureRandomBytes(32))

        /**
         * A SHA-256 hash value consisting of 32 0x00 bytes.
         * This field provides more intuitive access from Java.
         */
        @JvmField
        val zeroHash: SHA256 = SecureHash.SHA256(ByteArray(32, { 0.toByte() }))

        /**
         * A SHA-256 hash value consisting of 32 0x00 bytes.
         * This function is provided for API stability.
         */
        @Suppress("Unused")
        fun getZeroHash(): SHA256 = zeroHash

        /**
         * A SHA-256 hash value consisting of 32 0xFF bytes.
         * This field provides more intuitive access from Java.
         */
        @JvmField
        val allOnesHash: SHA256 = SecureHash.SHA256(ByteArray(32, { 255.toByte() }))

        /**
         * A SHA-256 hash value consisting of 32 0xFF bytes.
         * This function is provided for API stability.
         */
        @Suppress("Unused")
        fun getAllOnesHash(): SHA256 = allOnesHash
    }

    // In future, maybe SHA3, truncated hashes etc.
}

/**
 * Compute the SHA-256 hash for the contents of the [ByteArray].
 */
fun ByteArray.sha256(): SecureHash.SHA256 = SecureHash.sha256(this)

/**
 * Compute the SHA-256 hash for the contents of the [OpaqueBytes].
 */
fun OpaqueBytes.sha256(): SecureHash.SHA256 = SecureHash.sha256(this.bytes)

/**
 * Hide the [FastThreadLocal] class behind a [Supplier] interface
 * so that we can remove it for core-deterministic.
 */
private class SHA256DigestSupplier : Supplier<MessageDigest> {
    private val threadLocalSha256MessageDigest = LocalSHA256Digest()
    override fun get(): MessageDigest = threadLocalSha256MessageDigest.get()
}

// Declaring this as "object : FastThreadLocal<>" would have
// created an extra public class in the API definition.
private class LocalSHA256Digest : FastThreadLocal<MessageDigest>() {
    override fun initialValue(): MessageDigest = MessageDigest.getInstance("SHA-256")
}
