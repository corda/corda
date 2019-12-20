package net.corda.contracts.fixup.dependent

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

class DependentContract : Contract {
    companion object {
        const val MAX_BEANS = 1000L
    }

    override fun verify(tx: LedgerTransaction) {
        val states = tx.outputsOfType(State::class.java)
        require(states.isNotEmpty()) {
            "Requires at least one dependent data state"
        }

        states.forEach { state ->
            require(state.dependentData in DependentData(0)..DependentData(MAX_BEANS)) {
                "Invalid data: $state"
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class State(val owner: AbstractParty, val dependentData: DependentData) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return dependentData.toString()
        }
    }

    class Operate : CommandData
}