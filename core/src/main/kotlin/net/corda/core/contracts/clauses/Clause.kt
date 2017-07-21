package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor

/**
 * A clause of a contract, containing a chunk of verification logic. That logic may be delegated to other clauses, or
 * provided directly by this clause.
 *
 * @param S the type of contract state this clause operates on.
 * @param C a common supertype of commands this clause operates on.
 * @param K the type of the grouping key for states this clause operates on. Use [Unit] if not applicable.
 *
 * @see CompositeClause
 */
abstract class Clause<in S : ContractState, C : CommandData, in K : Any> {
    companion object {
        val log = loggerFor<Clause<*, *, *>>()
    }

    /** Determine whether this clause runs or not */
    open val requiredCommands: Set<Class<out CommandData>> = emptySet()

    /**
     * Determine the subclauses which will be verified as a result of verifying this clause.
     *
     * @throws IllegalStateException if the given commands do not result in a valid execution (for example no match
     * with [FirstOf]).
     */
    @Throws(IllegalStateException::class)
    open fun getExecutionPath(commands: List<AuthenticatedObject<C>>): List<Clause<*, *, *>>
            = listOf(this)

    /**
     * Verify the transaction matches the conditions from this clause. For example, a "no zero amount output" clause
     * would check each of the output states that it applies to, looking for a zero amount, and throw IllegalStateException
     * if any matched.
     *
     * @param tx the full transaction being verified. This is provided for cases where clauses need to access
     * states or commands outside of their normal scope.
     * @param inputs input states which are relevant to this clause. By default this is the set passed into [verifyClause],
     * but may be further reduced by clauses such as [GroupClauseVerifier].
     * @param outputs output states which are relevant to this clause. By default this is the set passed into [verifyClause],
     * but may be further reduced by clauses such as [GroupClauseVerifier].
     * @param commands commands which are relevant to this clause. By default this is the set passed into [verifyClause],
     * but may be further reduced by clauses such as [GroupClauseVerifier].
     * @param groupingKey a grouping key applied to states and commands, where applicable. Taken from
     * [LedgerTransaction.InOutGroup].
     * @return the set of commands that are consumed IF this clause is matched, and cannot be used to match a
     * later clause. This would normally be all commands matching "requiredCommands" for this clause, but some
     * verify() functions may do further filtering on possible matches, and return a subset. This may also include
     * commands that were not required (for example the Exit command for fungible assets is optional).
     */
    @Throws(IllegalStateException::class)
    abstract fun verify(tx: LedgerTransaction,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: K?): Set<C>
}

/**
 * Determine if the given list of commands matches the required commands for a clause to trigger.
 */
fun <C : CommandData> Clause<*, C, *>.matches(commands: List<AuthenticatedObject<C>>): Boolean {
    return if (requiredCommands.isEmpty())
        true
    else
        commands.map { it.value.javaClass }.toSet().containsAll(requiredCommands)
}
