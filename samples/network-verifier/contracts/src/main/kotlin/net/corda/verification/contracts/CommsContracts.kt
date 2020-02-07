package net.corda.verification.contracts

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction

@CordaSerializable
@BelongsToContract(CommsTestContract::class)
data class CommsTestState(val responses: List<String>, val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(issuer)
}

@CordaSerializable
object CommsTestCommand : CommandData

class CommsTestContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}
