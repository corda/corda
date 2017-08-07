package net.corda.core.crypto.testing

import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.AnonymousParty
import net.corda.core.serialization.CordaSerializable
import java.math.BigInteger
import java.security.PublicKey

@CordaSerializable
object NullPublicKey : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "NULL"
    override fun getEncoded() = byteArrayOf(0)
    override fun getFormat() = "NULL"
    override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
    override fun toString() = "NULL_KEY"
}

val NULL_PARTY = AnonymousParty(NullPublicKey)

// TODO: Clean up this duplication between Null and Dummy public key
@CordaSerializable
@Deprecated("Has encoding format problems, consider entropyToKeyPair() instead")
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
val NULL_SIGNATURE = TransactionSignature(ByteArray(32), NullPublicKey, SignatureMetadata(1, -1))