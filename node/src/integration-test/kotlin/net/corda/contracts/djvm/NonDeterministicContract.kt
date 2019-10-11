package net.corda.contracts.djvm

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant

class NonDeterministicContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        Instant.now().toString()
    }

    class State : ContractState {
        override val participants: List<AbstractParty> get() = emptyList()
    }

    object Cmd : TypeOnlyCommandData()
}
