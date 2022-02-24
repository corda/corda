package net.corda.core.transactions

import net.corda.core.contracts.NamedByHash
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class EncryptedTransaction (
        override val id : SecureHash,
        val bytes : ByteArray
        // TODO: will need to also store the signature of who verified this tx
        )  : NamedByHash{

    fun toVerified(verifierSignature: ByteArray) : VerifiedEncryptedTransaction {
        return VerifiedEncryptedTransaction(this, verifierSignature)
    }

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

@CordaSerializable
data class VerifiedEncryptedTransaction (
        val encryptedTransaction: EncryptedTransaction,
        val verifierSignature: ByteArray
)  : NamedByHash {

    constructor(id : SecureHash, bytes : ByteArray, verifierSignature: ByteArray) :
            this(EncryptedTransaction(id, bytes), verifierSignature)

    override val id: SecureHash
        get() = encryptedTransaction.id

    val bytes: ByteArray
        get() = encryptedTransaction.bytes

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VerifiedEncryptedTransaction

        if (encryptedTransaction != other.encryptedTransaction) return false
        if (!verifierSignature.contentEquals(other.verifierSignature)) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * (encryptedTransaction.hashCode() + verifierSignature.contentHashCode())
    }
}