package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState

/**
 * Abstract supertype for clauses which compose other clauses together in some logical manner.
 */
abstract class CompositeClause<in S : ContractState, C : CommandData, in K : Any> : Clause<S, C, K>() {
    /** List of clauses under this composite clause */
    abstract val clauses: List<Clause<S, C, K>>

    override fun getExecutionPath(commands: List<AuthenticatedObject<C>>): List<Clause<*, *, *>>
            = matchedClauses(commands).flatMap { it.getExecutionPath(commands) }

    /**
     * Determine which clauses are matched by the supplied commands.
     *
     * @throws IllegalStateException if the given commands do not result in a valid execution (for example no match
     * with [FirstOf]).
     */
    @Throws(IllegalStateException::class)
    abstract fun matchedClauses(commands: List<AuthenticatedObject<C>>): List<Clause<S, C, K>>
}
