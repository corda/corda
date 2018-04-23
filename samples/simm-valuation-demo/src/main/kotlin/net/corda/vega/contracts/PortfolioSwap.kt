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
import net.corda.core.transactions.LedgerTransaction

/**
 * Specifies the contract between two parties that are agreeing to a portfolio of trades and valuating that portfolio.
 * Implements an agree clause to agree to the portfolio and an update clause to change either the portfolio or valuation
 * of the portfolio arbitrarily.
 */
data class PortfolioSwap(private val blank: Void? = null) : Contract {
    override fun verify(tx: LedgerTransaction) {
        requireNotNull(tx.timeWindow) { "must have a time-window)" }
        val groups: List<LedgerTransaction.InOutGroup<PortfolioState, UniqueIdentifier>> = tx.groupStates { state -> state.linearId }
        for ((inputs, outputs, _) in groups) {
            val agreeCommand = tx.commands.select<Commands.Agree>().firstOrNull()
            if (agreeCommand != null) {
                requireThat {
                    "there are no inputs" using (inputs.isEmpty())
                    "there is one output" using (outputs.size == 1)
                    "valuer must be a party" using (outputs[0].participants.contains(outputs[0].valuer))
                }
            } else {
                val updateCommand = tx.commands.select<Commands.Update>().firstOrNull()
                if (updateCommand != null) {
                    requireThat {
                        "there is only one input" using (inputs.size == 1)
                        "there is only one output" using (outputs.size == 1)
                        "the valuer hasn't changed" using (inputs[0].valuer == outputs[0].valuer)
                        "the linear id hasn't changed" using (inputs[0].linearId == outputs[0].linearId)
                    }

                }
            }
        }
    }

    interface Commands : CommandData {
        class Agree : TypeOnlyCommandData(), Commands  // Both sides agree to portfolio
        class Update : TypeOnlyCommandData(), Commands // Both sides re-agree to portfolio
    }
}
