@file:JvmName("ClauseVerifier")
package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.*
import java.util.*

// Wrapper object for exposing a JVM friend version of the clause verifier
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
            MatchBehaviour.ERROR -> throw IllegalStateException("Error due to matching/not matching ${clause}")
            MatchBehaviour.CONTINUE -> {
            }
            MatchBehaviour.END -> break@verify
        }
    }

    require(unmatchedCommands.isEmpty()) { "All commands must be matched at end of execution." }
}

