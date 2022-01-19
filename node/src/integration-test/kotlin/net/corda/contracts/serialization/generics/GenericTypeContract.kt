package net.corda.contracts.serialization.generics

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.requireThat
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.util.Optional

@Suppress("unused")
class GenericTypeContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val states = tx.outputsOfType<State>()
        requireThat {
            "Requires at least one data state" using states.isNotEmpty()
        }
        val purchases = tx.commandsOfType<Purchase>()
        requireThat {
            "Requires at least one purchase" using purchases.isNotEmpty()
        }
        for (purchase in purchases) {
            requireThat {
                "Purchase has a price" using purchase.value.price.isPresent
            }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class State(val owner: AbstractParty, val data: DataObject?) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return data.toString()
        }
    }

    /**
     * The [price] field is the important feature of the [Purchase]
     * class because its type is [Optional] with a CorDapp-specific
     * generic type parameter. It does not matter that the [price]
     * is not used; it only matters that the [Purchase] command
     * must be serialized as part of building a new transaction.
     */
    class Purchase(val price: Optional<DataObject>) : CommandData
}