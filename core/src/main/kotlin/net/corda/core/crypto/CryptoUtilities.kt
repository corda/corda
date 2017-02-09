@file:JvmName("CryptoUtilities")

package net.corda.core.crypto

import net.corda.core.serialization.OpaqueBytes
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.*

fun newSecureRandom(): SecureRandom {
    if (System.getProperty("os.name") == "Linux") {
        return SecureRandom.getInstance("NativePRNGNonBlocking")
    } else {
        return SecureRandom.getInstanceStrong()
    }
}

/**
 * A wrapper around a digital signature. The covering field is a generic tag usable by whatever is interpreting the
 * signature. It isn't used currently, but experience from Bitcoin suggests such a feature is useful, especially when
 * building partially signed transactions.
 */
open class DigitalSignature(bits: ByteArray) : OpaqueBytes(bits) {
    /** A digital signature that identifies who the public key is owned by. */
    open class WithKey(val by: PublicKey, bits: ByteArray) : DigitalSignature(bits) {
        fun verifyWithECDSA(content: ByteArray) = by.verifyWithECDSA(content, this)
        fun verifyWithECDSA(content: OpaqueBytes) = by.verifyWithECDSA(content.bytes, this)
    }

    // TODO: consider removing this as whoever needs to identify the signer should be able to derive it from the public key
    class LegallyIdentifiable(val signer: Party, bits: ByteArray) : WithKey(signer.owningKey.singleKey, bits)
}

object NullPublicKey : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "NULL"
    override fun getEncoded() = byteArrayOf(0)
    override fun getFormat() = "NULL"
    override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
    override fun toString() = "NULL_KEY"
}

val NullCompositeKey = NullPublicKey.composite

// TODO: Clean up this duplication between Null and Dummy public key
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

fun parsePublicKeyBase58(base58String: String) = EdDSAPublicKey(EdDSAPublicKeySpec(Base58.decode(base58String), ed25519Curve))
fun PublicKey.toBase58String() = Base58.encode((this as EdDSAPublicKey).abyte)

fun KeyPair.signWithECDSA(bytesToSign: ByteArray) = private.signWithECDSA(bytesToSign, public)
fun KeyPair.signWithECDSA(bytesToSign: OpaqueBytes) = private.signWithECDSA(bytesToSign.bytes, public)
fun KeyPair.signWithECDSA(bytesToSign: OpaqueBytes, party: Party) = signWithECDSA(bytesToSign.bytes, party)
fun KeyPair.signWithECDSA(bytesToSign: ByteArray, party: Party): DigitalSignature.LegallyIdentifiable {
    check(public in party.owningKey.keys)
    val sig = signWithECDSA(bytesToSign)
    return DigitalSignature.LegallyIdentifiable(party, sig.bytes)
}

/** Utility to simplify the act of verifying a signature */
fun PublicKey.verifyWithECDSA(content: ByteArray, signature: DigitalSignature) {
    val verifier = EdDSAEngine()
    verifier.initVerify(this)
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

/** Creates a [CompositeKey] with a single leaf node containing the public key */
val PublicKey.composite: CompositeKey get() = CompositeKey.Leaf(this)

/** Returns the set of all [PublicKey]s of the signatures */
fun Iterable<DigitalSignature.WithKey>.byKeys() = map { it.by }.toSet()

// Allow Kotlin destructuring:    val (private, public) = keyPair
operator fun KeyPair.component1() = this.private

operator fun KeyPair.component2() = this.public

/** A simple wrapper that will make it easier to swap out the EC algorithm we use in future */
fun generateKeyPair(): KeyPair = KeyPairGenerator().generateKeyPair()

/**
 * Returns a key pair derived from the given entropy.
 *
 * @param entropy the seed in form of BigInteger.
 * @return the generated EdDSA keypair.
 */
fun entropyToKeyPair(entropy: BigInteger): KeyPair {
    return deterministicKeyPair(entropy.toByteArray())
}

/**
 * Returns a deterministically generated key pair from a given private key and an index (similarly to BIP32 hardened keys).
 * This is useful, as no backup of private keys is required and they can be re-generated from their parent key.
 * TODO: check with Mike if we need full implementation of the BIP32 protocol as we still do not support PublicParent -> PublicChild
 *
 * @param parentPrivateKey the parent private key from which we will generate a new keypair.
 * @param index a number input that will be concatenated to the  parent key to form the final seed.
 * @return the generated EdDSA keypair.
 */
fun privKeyToNewKeyPair(parentPrivateKey: EdDSAPrivateKey, index: Int): KeyPair {
    return deterministicKeyPair(parentPrivateKey.abyte.plus(index.bytes()))
}

/**
 * Returns a deterministically generated key pair from a provided seed. This is useful for deterministic
 * or hierarchical deterministic key derivation, unit tests and other cases where you want hard-coded private keys.
 *
 * @param bytes the seed for deterministic key generation.
 * @return the generated EdDSA keypair.
 */
fun deterministicKeyPair(bytes: ByteArray): KeyPair {
    val params = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.CURVE_ED25519_SHA512)
    val priv = EdDSAPrivateKeySpec(bytes, params)
    val pub = EdDSAPublicKeySpec(priv.a, params)
    val key = KeyPair(EdDSAPublicKey(pub), EdDSAPrivateKey(priv))
    return key
}

/*
 * Extension function to return the ByteArray representation of an Int.
 * TODO: check which of the bytes() and bytesV2() performs better
 */
fun Int.bytes()  =
        byteArrayOf(this.ushr(24).toByte(), this.ushr(16).toByte(), this.ushr(8).toByte(), this.toByte())

/*
 * Extension function to return the ByteArray representation of an Int.
 */
fun Int.bytesV2()  =
        ByteBuffer.allocate(4).putInt(this).array()
