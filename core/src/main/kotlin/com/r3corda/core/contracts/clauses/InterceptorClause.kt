package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.AuthenticatedObject
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.TransactionForContract
import java.util.*

/**
 * A clause which intercepts calls to a wrapped clause, and passes them through verification
 * only from a pre-clause. This is similar to an inceptor in aspect orientated programming.
 */
class InterceptorClause(
        val preclause: SingleVerify,
        val clause: SingleClause
) : SingleClause() {
    override val ifNotMatched: MatchBehaviour
        get() = clause.ifNotMatched
    override val ifMatched: MatchBehaviour
        get() = clause.ifMatched
    override val requiredCommands: Set<Class<out CommandData>>
        get() = clause.requiredCommands

    override fun verify(tx: TransactionForContract, commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData> {
        val consumed = HashSet(preclause.verify(tx, commands))
        consumed.addAll(clause.verify(tx, commands))
        return consumed
    }

    override fun toString(): String = "Interceptor clause [${clause}]"
}