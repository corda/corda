package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import java.util.*

/**
 * Compose a number of clauses, such that the first match is run, and it errors if none is run.
 */
class FirstOf<S : ContractState, C : CommandData, K : Any>(firstClause: Clause<S, C, K>, vararg remainingClauses: Clause<S, C, K>) : CompositeClause<S, C, K>() {
    companion object {
        val logger = loggerFor<FirstOf<*, *, *>>()
    }

    override val clauses = ArrayList<Clause<S, C, K>>()

    /**
     * Get the single matched clause from the set this composes, based on the given commands. This is provided as
     * helper method for internal use, rather than using the exposed [matchedClauses] function which unnecessarily
     * wraps the clause in a list.
     */
    private fun matchedClause(commands: List<AuthenticatedObject<C>>): Clause<S, C, K> {
        return clauses.firstOrNull { it.matches(commands) } ?: throw IllegalStateException("No delegate clause matched in first composition")
    }

    override fun matchedClauses(commands: List<AuthenticatedObject<C>>) = listOf(matchedClause(commands))

    init {
        clauses.add(firstClause)
        clauses.addAll(remainingClauses)
    }

    override fun verify(tx: LedgerTransaction, inputs: List<S>, outputs: List<S>, commands: List<AuthenticatedObject<C>>, groupingKey: K?): Set<C> {
        return matchedClause(commands).verify(tx, inputs, outputs, commands, groupingKey)
    }

    override fun toString() = "First: ${clauses.toList()}"
}
