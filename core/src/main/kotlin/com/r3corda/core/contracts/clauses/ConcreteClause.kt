package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.AuthenticatedObject
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.ContractState

/**
 * Abstract supertype for clauses which provide their own verification logic, rather than delegating to subclauses.
 * By default these clauses are always matched (they have no required commands).
 *
 * @see CompositeClause
 */
abstract class ConcreteClause<in S : ContractState, C: CommandData, in T : Any>: Clause<S, C, T> {
    override fun getExecutionPath(commands: List<AuthenticatedObject<C>>): List<Clause<*, *, *>>
        = listOf(this)
    override val requiredCommands: Set<Class<out CommandData>> = emptySet()
}