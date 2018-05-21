package net.corda.core.crypto

import net.corda.core.identity.AnonymousParty
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

object NullKeys {
    @CordaSerializable
    object NullPublicKey : PublicKey, Comparable<PublicKey> {
        override fun getAlgorithm() = "NULL"
        override fun getEncoded() = byteArrayOf(0)
        override fun getFormat() = "NULL"
        override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
        override fun toString() = "NULL_KEY"
    }

    val NULL_PARTY = AnonymousParty(NullPublicKey)

    /** A signature with a key and value of zero. Useful when you want a signature object that you know won't ever be used. */
    val NULL_SIGNATURE = TransactionSignature(ByteArray(32), NullPublicKey, SignatureMetadata(1, -1))
}