package net.corda.core.contracts.clauses

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState

/**
 * Compose a number of clauses, such that any number of the clauses can run.
 */
@Deprecated("Use AnyOf instead, although note that any of requires at least one matched clause")
class AnyComposition<in S : ContractState, C : CommandData, in K : Any>(vararg rawClauses: Clause<S, C, K>) : AnyOf<S, C, K>(*rawClauses)