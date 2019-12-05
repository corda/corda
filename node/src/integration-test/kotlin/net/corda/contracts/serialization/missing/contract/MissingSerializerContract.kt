package net.corda.contracts.serialization.missing.contract

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class MissingSerializerContract : Contract {
    companion object {
        const val MAX_VALUE = 2000
    }

    override fun verify(tx: LedgerTransaction) {
        val states = tx.outputsOfType(DataState::class.java)
        require(states.isNotEmpty()) {
            "Requires at least one data state"
        }

        states.forEach {
            require(it.data.value in 0..MAX_VALUE) {
                "Data ${it.data.value} exceeds maximum value!"
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class DataState(val owner: AbstractParty, val data: Data) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return data.toString()
        }
    }

    class Operate : CommandData
}