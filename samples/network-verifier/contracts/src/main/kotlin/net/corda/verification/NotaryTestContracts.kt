package net.corda.verification

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction

@CordaSerializable
data class NotaryTestState(val id: String, val issuer: AbstractParty) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(issuer)
}

@CordaSerializable
object NotaryTestCommand : CommandData

class NotaryTestContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}