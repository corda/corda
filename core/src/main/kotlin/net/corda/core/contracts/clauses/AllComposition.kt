package net.corda.core.contracts.clauses

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState

/**
 * Compose a number of clauses, such that all of the clauses must run for verification to pass.
 */
@Deprecated("Use AllOf")
class AllComposition<S : ContractState, C : CommandData, K : Any>(firstClause: Clause<S, C, K>, vararg remainingClauses: Clause<S, C, K>) : AllOf<S, C, K>(firstClause, *remainingClauses)