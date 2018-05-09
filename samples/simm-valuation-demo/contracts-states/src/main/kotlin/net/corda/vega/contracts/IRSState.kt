package net.corda.vega.contracts

import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.DealState

const val IRS_PROGRAM_ID: ContractClassName = "net.corda.vega.contracts.OGTrade"

/**
 * Represents an OpenGamma IRS between two parties. Does not implement any fixing functionality.
 *
 * TODO: Merge with the existing demo IRS code.
 */
data class IRSState(val swap: SwapData,
                    val buyer: AbstractParty,
                    val seller: AbstractParty,
                    override val linearId: UniqueIdentifier = UniqueIdentifier(swap.id.first + swap.id.second)) : DealState {
    val ref: String get() = linearId.externalId!! // Same as the constructor for UniqueIdentified
    override val participants: List<AbstractParty> get() = listOf(buyer, seller)

    override fun generateAgreement(notary: Party): TransactionBuilder {
        val state = IRSState(swap, buyer, seller)
        return TransactionBuilder(notary).withItems(StateAndContract(state, IRS_PROGRAM_ID), Command(OGTrade.Commands.Agree(), participants.map { it.owningKey }))
    }
}
