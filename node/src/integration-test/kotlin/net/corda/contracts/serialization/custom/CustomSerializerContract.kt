package net.corda.contracts.serialization.custom

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class CustomSerializerContract : Contract {
    companion object {
        const val MAX_CURRANT = 2000
    }

    override fun verify(tx: LedgerTransaction) {
        val currancyData = tx.outputsOfType(CurrancyState::class.java)
        require(currancyData.isNotEmpty()) {
            "Requires at least one currancy state"
        }

        currancyData.forEach {
            require(it.currancy.currants in 0..MAX_CURRANT) {
                "Too many currants! ${it.currancy.currants} is unraisinable!"
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class CurrancyState(val owner: AbstractParty, val currancy: Currancy) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return currancy.toString()
        }
    }

    class Purchase : CommandData
}