package net.corda.vega.contracts

import net.corda.core.contracts.Command
import net.corda.core.contracts.DealState
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

/**
 * Represents an OpenGamma IRS between two parties. Does not implement any fixing functionality.
 *
 * TODO: Merge with the existing demo IRS code.
 */
data class IRSState(val swap: SwapData,
                    val buyer: Party,
                    val seller: Party,
                    override val contract: OGTrade,
                    override val linearId: UniqueIdentifier = UniqueIdentifier(swap.id.first + swap.id.second)) : DealState {
    override val ref: String = linearId.externalId!! // Same as the constructor for UniqueIdentified
    override val parties: List<Party> get() = listOf(buyer, seller)

    override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
        return parties.flatMap { it.owningKey.keys }.intersect(ourKeys).isNotEmpty()
    }

    override fun generateAgreement(notary: Party): TransactionBuilder {
        val state = IRSState(swap, buyer, seller, OGTrade())
        return TransactionType.General.Builder(notary).withItems(state, Command(OGTrade.Commands.Agree(), parties.map { it.owningKey }))
    }

    override val participants: List<CompositeKey>
        get() = parties.map { it.owningKey }
}
