package net.corda.core.transactions

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/**
 * EncryptedTransaction wraps a serialized and encrypted enclave representation of a ledger transaction (a wire
 * transaction with inputs, references, attachments and network parameters).
 * @property id the hash of the [WireTransaction] Merkle tree root.
 * @property encryptedBytes the serialized and encrypted enclave ledger tx.
 * @property dependencies a set of transaction hashes this transaction depends on.
 * @property sigs a list of signatures from individual public keys.
 */
@CordaSerializable
data class EncryptedTransaction(
        override val id: SecureHash,
        val encryptedBytes: ByteArray,
        val dependencies: Set<SecureHash>,
        override val sigs: List<TransactionSignature>
) : TransactionWithSignatures {

    override val requiredSigningKeys: Set<PublicKey> = emptySet()

    override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedTransaction

        if (id != other.id) return false
        if (!encryptedBytes.contentEquals(other.encryptedBytes)) return false
        if (sigs != other.sigs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedBytes.contentHashCode()
        result = 31 * result + sigs.hashCode()
        return result
    }
}