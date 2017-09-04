package net.corda.vega.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.DealState

/**
 * Represents an OpenGamma IRS between two parties. Does not implement any fixing functionality.
 *
 * TODO: Merge with the existing demo IRS code.
 */
data class IRSState(val swap: SwapData,
                    val buyer: AbstractParty,
                    val seller: AbstractParty,
                    override val contract: OGTrade,
                    override val linearId: UniqueIdentifier = UniqueIdentifier(swap.id.first + swap.id.second)) : DealState {
    val ref: String get() = linearId.externalId!! // Same as the constructor for UniqueIdentified
    override val participants: List<AbstractParty> get() = listOf(buyer, seller)
    override val constraint get() = AlwaysAcceptAttachmentConstraint

    override fun generateAgreement(notary: Party): TransactionBuilder {
        val state = IRSState(swap, buyer, seller, OGTrade())
        return TransactionBuilder(notary).withItems(state, Command(OGTrade.Commands.Agree(), participants.map { it.owningKey }))
    }
}
