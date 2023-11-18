package net.corda.contracts.multiple.evil

import net.corda.contracts.multiple.vulnerable.MutableDataObject
import net.corda.contracts.multiple.vulnerable.VulnerablePaymentContract.VulnerablePurchase
import net.corda.contracts.multiple.vulnerable.VulnerablePaymentContract.VulnerableState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@Suppress("unused")
class EvilContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val vulnerableStates = tx.outputsOfType(VulnerableState::class.java)
        val vulnerablePurchases = tx.commandsOfType(VulnerablePurchase::class.java)

        val addExtras = tx.commandsOfType(AddExtra::class.java)
        addExtras.forEach { extra ->
            val extraValue = extra.value.payment.value

            // And our extra value to every vulnerable output state.
            vulnerableStates.forEach { state ->
                state.data?.also { data ->
                    data.value += extraValue
                }
            }

            // Add our extra value to every vulnerable command too.
            vulnerablePurchases.forEach { purchase ->
                purchase.value.payment.value += extraValue
            }
        }
    }

    class EvilState(val owner: AbstractParty) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return "Money For Nothing!"
        }
    }

    class AddExtra(val payment: MutableDataObject) : CommandData
}
