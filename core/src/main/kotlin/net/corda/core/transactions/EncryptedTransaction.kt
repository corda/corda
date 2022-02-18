package net.corda.core.transactions

import net.corda.core.contracts.NamedByHash
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class EncryptedTransaction (
        override val id : SecureHash,
        val bytes : ByteArray
        // TODO: will need to also store the signature of who verified this tx
        )  : NamedByHash{

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedTransaction

        if (id != other.id) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * (id.hashCode() + bytes.contentHashCode())
    }
}