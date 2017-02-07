package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionForContract
import net.corda.core.utilities.loggerFor
import java.util.*

/**
 * Compose a number of clauses, such that the first match is run, and it errors if none is run.
 */
@Deprecated("Use FirstOf instead")
class FirstComposition<S : ContractState, C : CommandData, K : Any>(val firstClause: Clause<S, C, K>, vararg remainingClauses: Clause<S, C, K>) : CompositeClause<S, C, K>() {
    companion object {
        val logger = loggerFor<FirstComposition<*, *, *>>()
    }

    override val clauses = ArrayList<Clause<S, C, K>>()
    override fun matchedClauses(commands: List<AuthenticatedObject<C>>): List<Clause<S, C, K>> = listOf(clauses.first { it.matches(commands) })

    init {
        clauses.add(firstClause)
        clauses.addAll(remainingClauses)
    }

    override fun verify(tx: TransactionForContract, inputs: List<S>, outputs: List<S>, commands: List<AuthenticatedObject<C>>, groupingKey: K?): Set<C> {
        val clause = matchedClauses(commands).singleOrNull() ?: throw IllegalStateException("No delegate clause matched in first composition")
        return clause.verify(tx, inputs, outputs, commands, groupingKey)
    }

    override fun toString() = "First: ${clauses.toList()}"
}
