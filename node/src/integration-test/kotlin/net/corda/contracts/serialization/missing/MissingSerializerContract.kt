package net.corda.contracts.serialization.missing

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class MissingSerializerContract : Contract {
    companion object {
        const val MAX_VALUE = 2000L
    }

    override fun verify(tx: LedgerTransaction) {
        val states = tx.outputsOfType(CustomDataState::class.java)
        require(states.isNotEmpty()) {
            "Requires at least one custom data state"
        }

        states.forEach {
            require(it.customData in CustomData(0)..CustomData(MAX_VALUE)) {
                "CustomData ${it.customData} exceeds maximum value!"
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class CustomDataState(val owner: AbstractParty, val customData: CustomData) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return customData.toString()
        }
    }

    class Operate : CommandData
}