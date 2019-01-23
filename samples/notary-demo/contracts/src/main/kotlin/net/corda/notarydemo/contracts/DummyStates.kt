package net.corda.notarydemo.contracts

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

const val DO_NOTHING_PROGRAM_ID = "net.corda.notarydemo.contracts.DoNothingContract"

class DoNothingContract : Contract {
    override fun verify(tx: LedgerTransaction) {}
}

data class DummyCommand(val dummy: Int = 0) : CommandData

@BelongsToContract(DoNothingContract::class)
data class DummyState(override val participants: List<AbstractParty>, val discriminator: Int) : ContractState
