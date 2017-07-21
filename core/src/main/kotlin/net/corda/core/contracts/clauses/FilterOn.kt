package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.transactions.LedgerTransaction

/**
 * Filter the states that are passed through to the wrapped clause, to restrict them to a specific type.
 */
class FilterOn<S : ContractState, C : CommandData, in K : Any>(val clause: Clause<S, C, K>,
                                                               val filterStates: (List<ContractState>) -> List<S>) : Clause<ContractState, C, K>() {
    override val requiredCommands: Set<Class<out CommandData>>
            = clause.requiredCommands

    override fun getExecutionPath(commands: List<AuthenticatedObject<C>>): List<Clause<*, *, *>>
            = clause.getExecutionPath(commands)

    override fun verify(tx: LedgerTransaction,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: K?): Set<C>
            = clause.verify(tx, filterStates(inputs), filterStates(outputs), commands, groupingKey)
}
