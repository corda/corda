@file:Suppress("MagicNumber")
package net.corda.core.solana

import net.corda.core.contracts.NotaryInstruction
import net.corda.core.crypto.Base58
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toBase58

/**
 * A Solana [NotaryInstruction] which the Solana notary will include it as an extra
 * [instruction](https://docs.rs/solana-instruction/2.3.0/solana_instruction/struct.Instruction.html) on the Solana transaction that
 * performs the Solana notary `commit` operation.
 */
class SolanaInstruction(
        val programId: Pubkey,
        val accounts: List<AccountMeta>,
        val data: OpaqueBytes
) : NotaryInstruction {
    override fun equals(other: Any?): Boolean {
        if (other !is SolanaInstruction) return false
        if (programId != other.programId) return false
        if (accounts != other.accounts) return false
        if (data != other.data) return false
        return true
    }

    override fun hashCode(): Int {
        var result = programId.hashCode()
        result = 31 * result + accounts.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }

    override fun toString(): String = "SolanaInstruction(programId=$programId, accounts=$accounts, data=$data)"
}

/**
 * Represents a Solana account which is used during instruction execution. This is equivalent to the Rust struct of the
 * [same name](https://docs.rs/solana-instruction/2.3.0/solana_instruction/account_meta/struct.AccountMeta.html).
 *
 * If this account is a signer ([isSigner] is `true`) then the Solana notary is required to have access to the corresponding private key and
 * sign the `commit` transaction as [pubkey].
 */
@CordaSerializable
class AccountMeta(
        val pubkey: Pubkey,
        val isSigner: Boolean,
        val isWritable: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (other !is AccountMeta) return false
        if (isSigner != other.isSigner) return false
        if (isWritable != other.isWritable) return false
        if (pubkey != other.pubkey) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isSigner.hashCode()
        result = 31 * result + isWritable.hashCode()
        result = 31 * result + pubkey.hashCode()
        return result
    }

    override fun toString(): String = "AccountMeta(pubkey=$pubkey, isSigner=$isSigner, isWritable=$isWritable)"
}

/**
 * Represents an address on Solana. This is equivalent to the Rust struct of the
 * [same name](https://docs.rs/solana-pubkey/2.4.0/solana_pubkey/struct.Pubkey.html).
 */
class Pubkey(bytes: ByteArray) : OpaqueBytes(bytes) {
    companion object {
        /**
         * Parse the given base58 string as a 32-byte Solana address.
         *
         * @throws net.corda.core.crypto.AddressFormatException If the given string is not valid base58.
         */
        @JvmStatic
        fun fromBase58(input: String): Pubkey = Pubkey(Base58.decode(input))
    }

    init {
        require(bytes.size == 32) { "Solana pubkey must be 32 bytes" }
    }

    override fun toString(): String = bytes.toBase58()
}
