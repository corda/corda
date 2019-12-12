package net.corda.contracts.serialization.custom

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class CustomSerializerContract : Contract {
    companion object {
        const val MAX_CURRANT = 2000L
    }

    override fun verify(tx: LedgerTransaction) {
        val currantsyData = tx.outputsOfType(CurrantsyState::class.java)
        require(currantsyData.isNotEmpty()) {
            "Requires at least one currantsy state"
        }

        currantsyData.forEach {
            require(it.currantsy in Currantsy(0)..Currantsy(MAX_CURRANT)) {
                "Too many currants! ${it.currantsy} is unraisinable!"
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class CurrantsyState(val owner: AbstractParty, val currantsy: Currantsy) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return currantsy.toString()
        }
    }

    class Purchase : CommandData
}