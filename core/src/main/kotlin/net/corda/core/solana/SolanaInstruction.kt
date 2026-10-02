@file:Suppress("MagicNumber")
package net.corda.core.solana

import net.corda.core.contracts.NotaryInstruction
import net.corda.core.utilities.OpaqueBytes

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

