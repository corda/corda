package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.AuthenticatedObject
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.TransactionForContract

/**
 * A clause that can be matched as part of execution of a contract.
 */
// TODO: ifNotMatched/ifMatched should be dropped, and replaced by logic in the calling code that understands
//       "or", "and", "single" etc. composition of sets of clauses.
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

/**
 * A single verifiable clause. By default always matches, continues to the next clause when matched and errors
 * if not matched.
 */
abstract class SingleClause : Clause, SingleVerify {
    override val ifMatched: MatchBehaviour = MatchBehaviour.CONTINUE
    override val ifNotMatched: MatchBehaviour = MatchBehaviour.ERROR
    override val requiredCommands: Set<Class<out CommandData>> = emptySet()
}