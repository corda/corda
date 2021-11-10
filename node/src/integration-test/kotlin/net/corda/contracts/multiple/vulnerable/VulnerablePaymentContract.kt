package net.corda.contracts.multiple.vulnerable

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.requireThat
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class VulnerablePaymentContract : Contract {
    companion object {
        const val BASE_PAYMENT = 2000L
    }

    override fun verify(tx: LedgerTransaction) {
        val states = tx.outputsOfType<VulnerableState>()
        requireThat {
            "Requires at least one data state" using states.isNotEmpty()
        }
        val purchases = tx.commandsOfType<VulnerablePurchase>()
        requireThat {
            "Requires at least one purchase" using purchases.isNotEmpty()
        }
        for (purchase in purchases) {
            val payment = purchase.value.payment
            requireThat {
                "Purchase payment of $payment should be at least $BASE_PAYMENT" using (payment.value >= BASE_PAYMENT)
            }
        }
    }

    class VulnerableState(val owner: AbstractParty, val data: MutableDataObject?) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return data.toString()
        }
    }

    class VulnerablePurchase(val payment: MutableDataObject) : CommandData
}
