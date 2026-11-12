package net.corda.core.solana

import net.corda.core.serialization.CordaSerializable

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