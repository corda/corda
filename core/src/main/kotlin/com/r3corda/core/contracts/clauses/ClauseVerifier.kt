package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.*
import java.util.*

interface Clause {
    /** Classes for commands which must ALL be present in transaction for this clause to be triggered */
    val requiredCommands: Set<Class<out CommandData>>
    /** Behaviour if this clause is matched */
    val ifNotMatched: MatchBehaviour
    /** Behaviour if this clause is not matches */
    val ifMatched: MatchBehaviour
}

enum class MatchBehaviour {
    CONTINUE,
    END,
    ERROR
}

interface SingleVerify {
    /**
     * Verify the transaction matches the conditions from this clause. For example, a "no zero amount output" clause
     * would check each of the output states that it applies to, looking for a zero amount, and throw IllegalStateException
     * if any matched.
     *
     * @return the set of commands that are consumed IF this clause is matched, and cannot be used to match a
     * later clause. This would normally be all commands matching "requiredCommands" for this clause, but some
     * verify() functions may do further filtering on possible matches, and return a subset. This may also include
     * commands that were not required (for example the Exit command for fungible assets is optional).
     */
    @Throws(IllegalStateException::class)
    fun verify(tx: TransactionForContract,
               commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData>

}


interface SingleClause : Clause, SingleVerify

/**
 * Abstract superclass for clause-based contracts to extend, which provides a verify() function
 * that delegates to the supplied list of clauses.
 */
abstract class ClauseVerifier : Contract {
    abstract val clauses: List<SingleClause>
    abstract fun extractCommands(tx: TransactionForContract): Collection<AuthenticatedObject<CommandData>>
    override fun verify(tx: TransactionForContract) = verifyClauses(tx, clauses, extractCommands(tx))
}

/**
 * Verify a transaction against the given list of clauses.
 *
 * @param tx transaction to be verified.
 * @param clauses the clauses to verify.
 * @param T common supertype of commands to extract from the transaction, which are of relevance to these clauses.
 */
inline fun <reified T : CommandData> verifyClauses(tx: TransactionForContract,
                                                   clauses: List<SingleClause>)
        = verifyClauses(tx, clauses, tx.commands.select<T>())

/**
 * Verify a transaction against the given list of clauses.
 *
 * @param tx transaction to be verified.
 * @param clauses the clauses to verify.
 * @param commands commands extracted from the transaction, which are relevant to the
 * clauses.
 */
fun verifyClauses(tx: TransactionForContract,
                  clauses: List<SingleClause>,
                  commands: Collection<AuthenticatedObject<CommandData>>) {
    val unmatchedCommands = ArrayList(commands.map { it.value })

    verify@ for (clause in clauses) {
        val matchBehaviour = if (unmatchedCommands.map { command -> command.javaClass }.containsAll(clause.requiredCommands)) {
            unmatchedCommands.removeAll(clause.verify(tx, commands))
            clause.ifMatched
        } else {
            clause.ifNotMatched
        }

        when (matchBehaviour) {
            MatchBehaviour.ERROR -> throw IllegalStateException()
            MatchBehaviour.CONTINUE -> {
            }
            MatchBehaviour.END -> break@verify
        }
    }

    require(unmatchedCommands.isEmpty()) { "All commands must be matched at end of execution." }
}