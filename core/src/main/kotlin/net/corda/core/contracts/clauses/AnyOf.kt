package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.transactions.LedgerTransaction
import java.util.*

/**
 * Compose a number of clauses, such that one or more of the clauses can run.
 */
open class AnyOf<in S : ContractState, C : CommandData, in K : Any>(vararg rawClauses: Clause<S, C, K>) : CompositeClause<S, C, K>() {
    override val clauses: List<Clause<S, C, K>> = rawClauses.toList()

    override fun matchedClauses(commands: List<AuthenticatedObject<C>>): List<Clause<S, C, K>> {
        val matched = clauses.filter { it.matches(commands) }
        require(matched.isNotEmpty()) { "At least one clause must match" }
        return matched
    }

    override fun verify(tx: LedgerTransaction, inputs: List<S>, outputs: List<S>, commands: List<AuthenticatedObject<C>>, groupingKey: K?): Set<C> {
        return matchedClauses(commands).flatMapTo(HashSet<C>()) { clause ->
            clause.verify(tx, inputs, outputs, commands, groupingKey)
        }
    }

    override fun toString(): String = "Any: ${clauses.toList()}"
}
