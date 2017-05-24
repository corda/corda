package net.corda.vega.contracts

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.*
import net.corda.core.crypto.SecureHash
import java.math.BigDecimal

/**
 * Specifies the contract between two parties that trade an OpenGamma IRS. Currently can only agree to trade.
 */
data class OGTrade(override val legalContractReference: SecureHash = SecureHash.sha256("OGTRADE.KT")) : Contract {
    override fun verify(tx: TransactionForContract) = verifyClause(tx, AllOf(Clauses.Timestamped(), Clauses.Group()), tx.commands.select<Commands>())

    interface Commands : CommandData {
        class Agree : TypeOnlyCommandData(), Commands  // Both sides agree to trade
    }

    interface Clauses {
        class Timestamped : Clause<ContractState, Commands, Unit>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(tx.timestamp?.midpoint != null) { "must be timestamped" }
                // We return an empty set because we don't process any commands
                return emptySet()
            }
        }

        class Group : GroupClauseVerifier<IRSState, Commands, UniqueIdentifier>(AnyOf(Agree())) {
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<IRSState, UniqueIdentifier>>
                    // Group by Trade ID for in / out states
                    = tx.groupStates { state -> state.linearId }
        }

        class Agree : Clause<IRSState, Commands, UniqueIdentifier>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<IRSState>,
                                outputs: List<IRSState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Agree>()

                require(inputs.size == 0) { "Inputs must be empty" }
                require(outputs.size == 1) { "" }
                require(outputs[0].buyer != outputs[0].seller)
                require(outputs[0].parties.containsAll(outputs[0].participants))
                require(outputs[0].parties.containsAll(listOf(outputs[0].buyer, outputs[0].seller)))
                require(outputs[0].swap.startDate.isBefore(outputs[0].swap.endDate))
                require(outputs[0].swap.notional > BigDecimal(0))
                require(outputs[0].swap.tradeDate.isBefore(outputs[0].swap.endDate))

                return setOf(command.value)
            }
        }
    }
}
