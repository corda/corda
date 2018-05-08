package net.corda.vega.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

/**
 * Specifies the contract between two parties that trade an OpenGamma IRS. Currently can only agree to trade.
 */
class OGTrade : Contract {
    override fun verify(tx: LedgerTransaction) {
        requireNotNull(tx.timeWindow) { "must have a time-window" }
        val groups: List<LedgerTransaction.InOutGroup<IRSState, UniqueIdentifier>> = tx.groupStates { state -> state.linearId }
        var atLeastOneCommandProcessed = false
        for ((inputs, outputs, _) in groups) {
            val command = tx.commands.select<Commands.Agree>().firstOrNull()
            if (command != null) {
                require(inputs.isEmpty()) { "Inputs must be empty" }
                require(outputs.size == 1) { "" }
                require(outputs[0].buyer != outputs[0].seller)
                require(outputs[0].participants.containsAll(outputs[0].participants))
                require(outputs[0].participants.containsAll(listOf(outputs[0].buyer, outputs[0].seller)))
                require(outputs[0].swap.startDate.isBefore(outputs[0].swap.endDate))
                require(outputs[0].swap.notional > BigDecimal(0))
                require(outputs[0].swap.tradeDate.isBefore(outputs[0].swap.endDate))
                atLeastOneCommandProcessed = true
            }
        }
        require(atLeastOneCommandProcessed) { "At least one command needs to present" }
    }

    interface Commands : CommandData {
        class Agree : TypeOnlyCommandData(), Commands  // Both sides agree to trade
    }
}
