package net.corda.contracts.djvm.crypto

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.Crypto
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey

class DeterministicCryptoContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val cryptoData = tx.outputsOfType<CryptoState>()
        val validators = tx.commandsOfType<Validate>()

        val isValid = validators.all { validate ->
            with (validate.value) {
                cryptoData.all { crypto ->
                    Crypto.doVerify(schemeCodeName, publicKey, crypto.signature.bytes, crypto.original.bytes)
                }
            }
        }

        require(cryptoData.isNotEmpty() && validators.isNotEmpty() && isValid) {
            "Failed to validate signatures in command data"
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class CryptoState(val owner: AbstractParty, val original: OpaqueBytes, val signature: OpaqueBytes) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)
    }

    class Validate(
        val schemeCodeName: String,
        val publicKey: PublicKey
    ) : CommandData
}