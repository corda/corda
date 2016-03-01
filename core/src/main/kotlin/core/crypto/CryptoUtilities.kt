/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.crypto

import com.google.common.io.BaseEncoding
import core.Party
import core.serialization.OpaqueBytes
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey

// "sealed" here means there can't be any subclasses other than the ones defined here.
sealed class SecureHash private constructor(bits: ByteArray) : OpaqueBytes(bits) {
    class SHA256(bits: ByteArray) : SecureHash(bits) {
        init { require(bits.size == 32) }
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
                else -> throw IllegalArgumentException("Provided string is not 32 bytes in base 16 (hex): $str")
            }
        }

        @JvmStatic fun sha256(bits: ByteArray) = SHA256(MessageDigest.getInstance("SHA-256").digest(bits))
        @JvmStatic fun sha256Twice(bits: ByteArray) = sha256(sha256(bits).bits)
        @JvmStatic fun sha256(str: String) = sha256(str.toByteArray())

        @JvmStatic fun randomSHA256() = sha256(SecureRandom.getInstanceStrong().generateSeed(32))
    }

    abstract val signatureAlgorithmName: String

    // In future, maybe SHA3, truncated hashes etc.
}

fun ByteArray.sha256(): SecureHash.SHA256 = SecureHash.sha256(this)
fun OpaqueBytes.sha256(): SecureHash.SHA256 = SecureHash.sha256(this.bits)

/**
 * A wrapper around a digital signature. The covering field is a generic tag usable by whatever is interpreting the
 * signature. It isn't used currently, but experience from Bitcoin suggests such a feature is useful, especially when
 * building partially signed transactions.
 */
open class DigitalSignature(bits: ByteArray, val covering: Int = 0) : OpaqueBytes(bits) {

    /** A digital signature that identifies who the public key is owned by. */
    open class WithKey(val by: PublicKey, bits: ByteArray, covering: Int = 0) : DigitalSignature(bits, covering) {
        fun verifyWithECDSA(content: ByteArray) = by.verifyWithECDSA(content, this)
        fun verifyWithECDSA(content: OpaqueBytes) = by.verifyWithECDSA(content.bits, this)
    }

    class LegallyIdentifiable(val signer: Party, bits: ByteArray, covering: Int) : WithKey(signer.owningKey, bits, covering)
}

object NullPublicKey : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "NULL"
    override fun getEncoded() = byteArrayOf(0)
    override fun getFormat() = "NULL"
    override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
    override fun toString() = "NULL_KEY"
}

class DummyPublicKey(val s: String) : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "DUMMY"
    override fun getEncoded() = s.toByteArray()
    override fun getFormat() = "ASN.1"
    override fun compareTo(other: PublicKey): Int = BigInteger(encoded).compareTo(BigInteger(other.encoded))
    override fun equals(other: Any?) = other is DummyPublicKey && other.s == s
    override fun hashCode(): Int = s.hashCode()
    override fun toString() = "PUBKEY[$s]"
}

/** Utility to simplify the act of signing a byte array */
fun PrivateKey.signWithECDSA(bits: ByteArray): DigitalSignature {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(this)
    signer.update(bits)
    val sig = signer.sign()
    return DigitalSignature(sig)
}

fun PrivateKey.signWithECDSA(bitsToSign: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
    return DigitalSignature.WithKey(publicKey, signWithECDSA(bitsToSign).bits)
}
fun KeyPair.signWithECDSA(bitsToSign: ByteArray) = private.signWithECDSA(bitsToSign, public)
fun KeyPair.signWithECDSA(bitsToSign: OpaqueBytes) = private.signWithECDSA(bitsToSign.bits, public)
fun KeyPair.signWithECDSA(bitsToSign: ByteArray, party: Party): DigitalSignature.LegallyIdentifiable {
    check(public == party.owningKey)
    val sig = signWithECDSA(bitsToSign)
    return DigitalSignature.LegallyIdentifiable(party, sig.bits, 0)
}

/** Utility to simplify the act of verifying a signature */
fun PublicKey.verifyWithECDSA(content: ByteArray, signature: DigitalSignature) {
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(this)
    verifier.update(content)
    if (verifier.verify(signature.bits) == false)
        throw SignatureException("Signature did not match")
}

/** Render a public key to a string, using a short form if it's an elliptic curve public key */
fun PublicKey.toStringShort(): String {
    return (this as? ECPublicKey)?.let { key ->
        "DL" + Base58.encode(key.w.affineX.toByteArray())   // DL -> Distributed Ledger
    } ?: toString()
}

// Allow Kotlin destructuring:    val (private, public) = keypair
operator fun KeyPair.component1() = this.private
operator fun KeyPair.component2() = this.public

/** A simple wrapper that will make it easier to swap out the EC algorithm we use in future */
fun generateKeyPair() = KeyPairGenerator.getInstance("EC").genKeyPair()