package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionForContract
import java.util.*

abstract class GroupClauseVerifier<S : ContractState, C : CommandData, K : Any>(val clause: Clause<S, C, K>) : Clause<ContractState, C, Unit>() {
    abstract fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<S, K>>

    override fun getExecutionPath(commands: List<AuthenticatedObject<C>>): List<Clause<*, *, *>>
            = clause.getExecutionPath(commands)

    override fun verify(tx: TransactionForContract,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: Unit?): Set<C> {
        val groups = groupStates(tx)
        val matchedCommands = HashSet<C>()

        for ((groupInputs, groupOutputs, groupToken) in groups) {
            matchedCommands.addAll(clause.verify(tx, groupInputs, groupOutputs, commands, groupToken))
        }

        return matchedCommands
    }
}
