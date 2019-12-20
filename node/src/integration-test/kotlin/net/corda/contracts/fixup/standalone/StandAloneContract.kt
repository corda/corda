package net.corda.contracts.fixup.standalone

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class StandAloneContract : Contract {
    companion object {
        const val MAX_PODS = 1000L
    }

    override fun verify(tx: LedgerTransaction) {
        val states = tx.outputsOfType(State::class.java)
        require(states.isNotEmpty()) {
            "Requires at least one standalone data state"
        }

        states.forEach { state ->
            require(state.standAloneData in StandAloneData(0)..StandAloneData(MAX_PODS)) {
                "Invalid data: $state"
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class State(val owner: AbstractParty, val standAloneData: StandAloneData) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return standAloneData.toString()
        }
    }

    class Operate : CommandData
}