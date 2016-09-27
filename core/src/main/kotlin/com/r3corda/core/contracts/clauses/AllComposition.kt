package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.AuthenticatedObject
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.TransactionForContract
import java.util.*

/**
 * Compose a number of clauses, such that all of the clauses must run for verification to pass.
 */
// TODO: Rename to AllOf
class AllComposition<S : ContractState, C : CommandData, K : Any>(firstClause: Clause<S, C, K>, vararg remainingClauses: Clause<S, C, K>) : CompositeClause<S, C, K>() {
    override val clauses = ArrayList<Clause<S, C, K>>()

    init {
        clauses.add(firstClause)
        clauses.addAll(remainingClauses)
    }

    override fun matchedClauses(commands: List<AuthenticatedObject<C>>): List<Clause<S, C, K>> {
        clauses.forEach { clause ->
            check(clause.matches(commands)) { "Failed to match clause ${clause}" }
        }
        return clauses
    }

    override fun verify(tx: TransactionForContract,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: K?): Set<C> {
        return matchedClauses(commands).flatMapTo(HashSet<C>()) { clause ->
            clause.verify(tx, inputs, outputs, commands, groupingKey)
        }
    }

    override fun toString() = "All: $clauses.toList()"
}