package net.corda.vega.contracts

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.*
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.LedgerTransaction

/**
 * Specifies the contract between two parties that are agreeing to a portfolio of trades and valuating that portfolio.
 * Implements an agree clause to agree to the portfolio and an update clause to change either the portfolio or valuation
 * of the portfolio arbitrarily.
 */
data class PortfolioSwap(override val legalContractReference: SecureHash = SecureHash.sha256("swordfish")) : Contract {
    override fun verify(tx: LedgerTransaction) = verifyClause(tx, AllOf(Clauses.TimeWindowed(), Clauses.Group()), tx.commands.select<Commands>())

    interface Commands : CommandData {
        class Agree : TypeOnlyCommandData(), Commands  // Both sides agree to portfolio
        class Update : TypeOnlyCommandData(), Commands // Both sides re-agree to portfolio
    }

    interface Clauses {
        class TimeWindowed : Clause<ContractState, Commands, Unit>() {
            override fun verify(tx: LedgerTransaction,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                requireNotNull(tx.timeWindow) { "must have a time-window)" }
                // We return an empty set because we don't process any commands
                return emptySet()
            }
        }

        class Group : GroupClauseVerifier<PortfolioState, Commands, UniqueIdentifier>(FirstOf(Agree(), Update())) {
            override fun groupStates(tx: LedgerTransaction): List<LedgerTransaction.InOutGroup<PortfolioState, UniqueIdentifier>>
                    // Group by Trade ID for in / out states
                    = tx.groupStates { state -> state.linearId }
        }

        class Update : Clause<PortfolioState, Commands, UniqueIdentifier>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Update::class.java)

            override fun verify(tx: LedgerTransaction,
                                inputs: List<PortfolioState>,
                                outputs: List<PortfolioState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Update>()

                requireThat {
                    "there is only one input" using (inputs.size == 1)
                    "there is only one output" using (outputs.size == 1)
                    "the valuer hasn't changed" using (inputs[0].valuer == outputs[0].valuer)
                    "the linear id hasn't changed" using (inputs[0].linearId == outputs[0].linearId)
                }

                return setOf(command.value)
            }
        }

        class Agree : Clause<PortfolioState, Commands, UniqueIdentifier>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Agree::class.java)

            override fun verify(tx: LedgerTransaction,
                                inputs: List<PortfolioState>,
                                outputs: List<PortfolioState>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: UniqueIdentifier?): Set<Commands> {
                val command = tx.commands.requireSingleCommand<Commands.Agree>()

                requireThat {
                    "there are no inputs" using (inputs.isEmpty())
                    "there is one output" using (outputs.size == 1)
                    "valuer must be a party" using (outputs[0].participants.contains(outputs[0].valuer))
                }

                return setOf(command.value)
            }
        }
    }
}
