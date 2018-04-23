/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.vega.contracts

import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.DealState
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

val PORTFOLIO_SWAP_PROGRAM_ID = "net.corda.vega.contracts.PortfolioSwap"

/**
 * Represents an aggregate set of trades agreed between two parties and a possible valuation of that portfolio at a
 * given point in time. This state can be consumed to create a new state with a mutated valuation or portfolio.
 */
data class PortfolioState(val portfolio: List<StateRef>,
                          val _parties: Pair<AbstractParty, AbstractParty>,
                          val valuationDate: LocalDate,
                          val valuation: PortfolioValuation? = null,
                          override val linearId: UniqueIdentifier = UniqueIdentifier())
    : RevisionedState<PortfolioState.Update>, SchedulableState, DealState {
    @CordaSerializable
    data class Update(val portfolio: List<StateRef>? = null, val valuation: PortfolioValuation? = null)

    override val participants: List<AbstractParty> get() = _parties.toList()
    val ref: String get() = linearId.toString()
    val valuer: AbstractParty get() = participants[0]

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity {
        val flow = flowLogicRefFactory.create("net.corda.vega.flows.SimmRevaluation\$Initiator", thisStateRef, LocalDate.now())
        return ScheduledActivity(flow, LocalDate.now().plus(1, ChronoUnit.DAYS).atStartOfDay().toInstant(ZoneOffset.UTC))
    }

    override fun generateAgreement(notary: Party): TransactionBuilder {
        return TransactionBuilder(notary).withItems(StateAndContract(copy(), PORTFOLIO_SWAP_PROGRAM_ID), Command(PortfolioSwap.Commands.Agree(), participants.map { it.owningKey }))
    }

    override fun generateRevision(notary: Party, oldState: StateAndRef<*>, updatedValue: Update): TransactionBuilder {
        require(oldState.state.data == this)
        val portfolio = updatedValue.portfolio ?: portfolio
        val valuation = updatedValue.valuation ?: valuation

        val tx = TransactionBuilder(notary)
        tx.addInputState(oldState)
        tx.addOutputState(copy(portfolio = portfolio, valuation = valuation), PORTFOLIO_SWAP_PROGRAM_ID)
        tx.addCommand(PortfolioSwap.Commands.Update(), participants.map { it.owningKey })
        return tx
    }
}
