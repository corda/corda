package core

import com.google.common.io.BaseEncoding
import java.security.MessageDigest
import java.security.PublicKey

// "sealed" here means there can't be any subclasses other than the ones defined here.
sealed class SecureHash(bits: ByteArray) : OpaqueBytes(bits) {
    class SHA256(bits: ByteArray) : SecureHash(bits) {
        init { require(bits.size == 32) }
    }

    // Like static methods in Java, except the 'companion' is a singleton that can have state.
    companion object {
        fun parse(str: String) = BaseEncoding.base16().decode(str.toLowerCase()).let {
            when (it.size) {
                32 -> SecureHash.SHA256(it)
                else -> throw IllegalArgumentException("Provided string is not 32 bytes in base 16 (hex): $str")
            }
        }

        fun sha256(bits: ByteArray) = SHA256(MessageDigest.getInstance("SHA-256").digest(bits))
        fun sha256(str: String) = sha256(str.toByteArray())
    }

    // In future, maybe SHA3, truncated hashes etc.
}

/**
 * A wrapper around a digital signature. The covering field is a generic tag usable by whatever is interpreting the
 * signature.
 */
sealed class DigitalSignature(bits: ByteArray, val covering: Int) : OpaqueBytes(bits) {
    /** A digital signature that identifies who the public key is owned by */
    open class WithKey(val by: PublicKey, bits: ByteArray, covering: Int) : DigitalSignature(bits, covering)
    class LegallyIdentifiable(val signer: Institution, bits: ByteArray, covering: Int) : WithKey(signer.owningKey, bits, covering)
}

object NullPublicKey : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "NULL"
    override fun getEncoded() = byteArrayOf(0)
    override fun getFormat() = "NULL"
    override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
    override fun toString() = "NULL_KEY"
}
