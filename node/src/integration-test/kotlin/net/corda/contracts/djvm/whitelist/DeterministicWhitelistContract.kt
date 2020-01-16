package net.corda.contracts.djvm.whitelist

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

class DeterministicWhitelistContract : Contract {
    companion object {
        const val MAX_VALUE = 2000L
    }

    override fun verify(tx: LedgerTransaction) {
        val states = tx.outputsOfType<State>()
        require(states.isNotEmpty()) {
            "Requires at least one custom data state"
        }

        states.forEach {
            require(it.whitelistData in WhitelistData(0)..WhitelistData(MAX_VALUE)) {
                "WhitelistData $it exceeds maximum value!"
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class State(val owner: AbstractParty, val whitelistData: WhitelistData) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return whitelistData.toString()
        }
    }

    class Operate : CommandData
}