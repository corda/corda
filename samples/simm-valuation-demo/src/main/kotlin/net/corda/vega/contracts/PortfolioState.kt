package net.corda.vega.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.transactions.TransactionBuilder
import net.corda.vega.flows.SimmRevaluation
import java.security.PublicKey
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Represents an aggregate set of trades agreed between two parties and a possible valuation of that portfolio at a
 * given point in time. This state can be consumed to create a new state with a mutated valuation or portfolio.
 */
data class PortfolioState(val portfolio: List<StateRef>,
                          override val contract: PortfolioSwap,
                          private val _parties: Pair<Party, Party>,
                          val valuationDate: LocalDate,
                          val valuation: PortfolioValuation? = null,
                          override val linearId: UniqueIdentifier = UniqueIdentifier())
    : RevisionedState<PortfolioState.Update>, SchedulableState, DealState {
    data class Update(val portfolio: List<StateRef>? = null, val valuation: PortfolioValuation? = null)

    override val parties: List<Party> get() = _parties.toList()
    override val ref: String = linearId.toString()
    val valuer: Party get() = parties[0]

    override val participants: List<CompositeKey>
        get() = parties.map { it.owningKey }

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity {
        val flow = flowLogicRefFactory.create(SimmRevaluation.Initiator::class.java, thisStateRef, LocalDate.now())
        return ScheduledActivity(flow, LocalDate.now().plus(1, ChronoUnit.DAYS).atStartOfDay().toInstant(ZoneOffset.UTC))
    }

    override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
        return parties.flatMap { it.owningKey.keys }.intersect(ourKeys).isNotEmpty()
    }

    override fun generateAgreement(notary: Party): TransactionBuilder {
        return TransactionType.General.Builder(notary).withItems(copy(), Command(PortfolioSwap.Commands.Agree(), parties.map { it.owningKey }))
    }

    override fun generateRevision(notary: Party, oldState: StateAndRef<*>, updatedValue: Update): TransactionBuilder {
        require(oldState.state.data == this)
        val portfolio = updatedValue.portfolio ?: portfolio
        val valuation = updatedValue.valuation ?: valuation

        val tx = TransactionType.General.Builder(notary)
        tx.addInputState(oldState)
        tx.addOutputState(copy(portfolio = portfolio, valuation = valuation))
        tx.addCommand(PortfolioSwap.Commands.Update(), parties.map { it.owningKey })
        return tx
    }
}
