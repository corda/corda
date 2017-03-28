@file:JvmName("CryptoUtilities")

package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.math.BigInteger
import java.security.*

// TODO We need to specify if we want to support signatures for CompositeKeys. In that case bits should have specified format.
/** A wrapper around a digital signature. */
@CordaSerializable
open class DigitalSignature(bits: ByteArray) : OpaqueBytes(bits) {
    /** A digital signature that identifies who the public key is owned by. */
    open class WithKey(val by: PublicKey, bits: ByteArray) : DigitalSignature(bits) {
        fun verifyWithECDSA(content: ByteArray) = by.verifyWithECDSA(content, this)
        fun verifyWithECDSA(content: OpaqueBytes) = by.verifyWithECDSA(content.bytes, this)
    }

    // TODO: consider removing this as whoever needs to identify the signer should be able to derive it from the public key
    class LegallyIdentifiable(val signer: Party, bits: ByteArray) : WithKey(signer.owningKey.composite.singleKey, bits)
}

@CordaSerializable
object NullPublicKey : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "NULL"
    override fun getEncoded() = byteArrayOf(0)
    override fun getFormat() = "NULL"
    override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
    override fun toString() = "NULL_KEY"
}

val NullCompositeKey = NullPublicKey.composite

// TODO: Clean up this duplication between Null and Dummy public key
@CordaSerializable
class DummyPublicKey(val s: String) : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "DUMMY"
    override fun getEncoded() = s.toByteArray()
    override fun getFormat() = "ASN.1"
    override fun compareTo(other: PublicKey): Int = BigInteger(encoded).compareTo(BigInteger(other.encoded))
    override fun equals(other: Any?) = other is DummyPublicKey && other.s == s
    override fun hashCode(): Int = s.hashCode()
    override fun toString() = "PUBKEY[$s]"
}

/** A signature with a key and value of zero. Useful when you want a signature object that you know won't ever be used. */
@CordaSerializable
object NullSignature : DigitalSignature.WithKey(NullPublicKey, ByteArray(32))

/** Utility to simplify the act of signing a byte array */
fun PrivateKey.signWithECDSA(bytes: ByteArray): DigitalSignature {
    val signer = EdDSAEngine()
    signer.initSign(this)
    signer.update(bytes)
    val sig = signer.sign()
    return DigitalSignature(sig)
}

fun PrivateKey.signWithECDSA(bytesToSign: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
    return DigitalSignature.WithKey(publicKey, signWithECDSA(bytesToSign).bytes)
}

val ed25519Curve = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.CURVE_ED25519_SHA512)

// TODO We use for both CompositeKeys and EdDSAPublicKey custom Kryo serializers and deserializers. We need to specify encoding.
// TODO: follow the crypto-conditions ASN.1 spec, some changes are needed to be compatible with the condition
//       structure, e.g. mapping a PublicKey to a condition with the specific feature (ED25519).
fun parsePublicKeyBase58(base58String: String): PublicKey = Base58.decode(base58String).deserialize<PublicKey>()
fun PublicKey.toBase58String(): String = Base58.encode(this.serialize().bytes)

fun KeyPair.signWithECDSA(bytesToSign: ByteArray) = private.signWithECDSA(bytesToSign, public)
fun KeyPair.signWithECDSA(bytesToSign: OpaqueBytes) = private.signWithECDSA(bytesToSign.bytes, public)
fun KeyPair.signWithECDSA(bytesToSign: OpaqueBytes, party: Party) = signWithECDSA(bytesToSign.bytes, party)
// TODO This case will need more careful thinking, as party owningKey can be a CompositeKey. One way of doing that is
//  implementation of CompositeSignature.
@Throws(IllegalStateException::class)
fun KeyPair.signWithECDSA(bytesToSign: ByteArray, party: Party): DigitalSignature.LegallyIdentifiable {
    val sig = signWithECDSA(bytesToSign)
    val sigKey = when (party.owningKey) { // Quick workaround when we have CompositeKey as Party owningKey.
        is CompositeKey -> party.owningKey.singleKey
        else -> party.owningKey
    }
    sigKey.verifyWithECDSA(bytesToSign, sig)
    return DigitalSignature.LegallyIdentifiable(party, sig.bytes)
}

/** Utility to simplify the act of verifying a signature */
@Throws(SignatureException::class, IllegalStateException::class)
fun PublicKey.verifyWithECDSA(content: ByteArray, signature: DigitalSignature) {
    val pubKey = when (this) {
        is CompositeKey -> singleKey // TODO CompositeSignature verification.
        else -> this
    }
    val verifier = EdDSAEngine()
    verifier.initVerify(pubKey)
    verifier.update(content)
    if (verifier.verify(signature.bytes) == false)
        throw SignatureException("Signature did not match")
}

/** Render a public key to a string, using a short form if it's an elliptic curve public key */
fun PublicKey.toStringShort(): String {
    return (this as? EdDSAPublicKey)?.let { key ->
        "DL" + Base58.encode(key.abyte)   // DL -> Distributed Ledger
    } ?: toString()
}

/**
 * If got simple single PublicKey creates a [CompositeKey] with a single leaf node containing the public key.
 *  Type checks if obtained PublicKey is a CompositeKey, in that case returns itself.
 */
val PublicKey.composite: CompositeKey get() {
    if (this is CompositeKey) return this
    else return CompositeKey(1, listOf(this), listOf(1))
}

val PublicKey.keys: Set<PublicKey> get() {
    return if (this is CompositeKey) this.keys
    else setOf(this)
}

/** Returns the set of all [PublicKey]s of the signatures */
fun Iterable<DigitalSignature.WithKey>.byKeys() = map { it.by }.toSet()

// Allow Kotlin destructuring:    val (private, public) = keyPair
operator fun KeyPair.component1() = this.private

operator fun KeyPair.component2() = this.public

/** A simple wrapper that will make it easier to swap out the EC algorithm we use in future */
fun generateKeyPair(): KeyPair = KeyPairGenerator().generateKeyPair()

/**
 * Returns a key pair derived from the given private key entropy. This is useful for unit tests and other cases where
 * you want hard-coded private keys.
 */
fun entropyToKeyPair(entropy: BigInteger): KeyPair {
    val params = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.CURVE_ED25519_SHA512)
    val bytes = entropy.toByteArray().copyOf(params.curve.field.getb() / 8)
    val priv = EdDSAPrivateKeySpec(bytes, params)
    val pub = EdDSAPublicKeySpec(priv.a, params)
    val key = KeyPair(EdDSAPublicKey(pub), EdDSAPrivateKey(priv))
    return key
}
