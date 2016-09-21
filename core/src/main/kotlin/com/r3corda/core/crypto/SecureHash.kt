package com.r3corda.core.crypto

import com.google.common.io.BaseEncoding
import com.r3corda.core.serialization.OpaqueBytes
import java.security.MessageDigest

sealed class SecureHash(bits: ByteArray) : OpaqueBytes(bits) {
    class SHA256(bits: ByteArray) : SecureHash(bits) {
        init {
            require(bits.size == 32)
        }

        override val signatureAlgorithmName: String get() = "SHA256withECDSA"
    }

    override fun toString() = BaseEncoding.base16().encode(bits)

    fun prefixChars(prefixLen: Int = 6) = toString().substring(0, prefixLen)

    // Like static methods in Java, except the 'companion' is a singleton that can have state.
    companion object {
        @JvmStatic
        fun parse(str: String) = BaseEncoding.base16().decode(str.toUpperCase()).let {
            when (it.size) {
                32 -> SHA256(it)
                else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 32 bytes in hex: $str")
            }
        }

        @JvmStatic fun sha256(bits: ByteArray) = SHA256(MessageDigest.getInstance("SHA-256").digest(bits))
        @JvmStatic fun sha256Twice(bits: ByteArray) = sha256(sha256(bits).bits)
        @JvmStatic fun sha256(str: String) = sha256(str.toByteArray())

        @JvmStatic fun randomSHA256() = sha256(newSecureRandom().generateSeed(32))
    }

    abstract val signatureAlgorithmName: String

    // In future, maybe SHA3, truncated hashes etc.
}

fun ByteArray.sha256(): SecureHash.SHA256 = SecureHash.sha256(this)
fun OpaqueBytes.sha256(): SecureHash.SHA256 = SecureHash.sha256(this.bits)