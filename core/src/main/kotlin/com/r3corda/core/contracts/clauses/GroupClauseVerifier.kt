package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.AuthenticatedObject
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.TransactionForContract
import java.util.*

interface GroupVerify<in S, in T : Any> {
    /**
     *
     * @return the set of commands that are consumed IF this clause is matched, and cannot be used to match a
     * later clause.
     */
    fun verify(tx: TransactionForContract,
               inputs: List<S>,
               outputs: List<S>,
               commands: Collection<AuthenticatedObject<CommandData>>,
               token: T): Set<CommandData>
}

interface GroupClause<in S : ContractState, in T : Any> : Clause, GroupVerify<S, T>

abstract class GroupClauseVerifier<S : ContractState, T : Any> : SingleClause {
    abstract val clauses: List<GroupClause<S, T>>
    override val requiredCommands: Set<Class<out CommandData>>
        get() = emptySet()

    abstract fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<S, T>>

    override fun verify(tx: TransactionForContract, commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData> {
        val groups = groupStates(tx)
        val matchedCommands = HashSet<CommandData>()
        val unmatchedCommands = ArrayList(commands.map { it.value })

        for ((inputs, outputs, token) in groups) {
            val temp = verifyGroup(commands, inputs, outputs, token, tx, unmatchedCommands)
            matchedCommands.addAll(temp)
            unmatchedCommands.removeAll(temp)
        }

        return matchedCommands
    }

    /**
     * Verify a subset of a transaction's inputs and outputs matches the conditions from this clause. For example, a
     * "no zero amount output" clause would check each of the output states within the group, looking for a zero amount,
     * and throw IllegalStateException if any matched.
     *
     * @param commands the full set of commands which apply to this contract.
     * @param inputs input states within this group.
     * @param outputs output states within this group.
     * @param token the object used as a key when grouping states.
     * @param unmatchedCommands commands which have not yet been matched within this group.
     * @return matchedCommands commands which are matched during the verification process.
     */
    @Throws(IllegalStateException::class)
    private fun verifyGroup(commands: Collection<AuthenticatedObject<CommandData>>,
                            inputs: List<S>,
                            outputs: List<S>,
                            token: T,
                            tx: TransactionForContract,
                            unmatchedCommands: List<CommandData>): Set<CommandData> {
        val matchedCommands = HashSet<CommandData>()
        verify@ for (clause in clauses) {
            val matchBehaviour = if (unmatchedCommands.map { command -> command.javaClass }.containsAll(clause.requiredCommands)) {
                matchedCommands.addAll(clause.verify(tx, inputs, outputs, commands, token))
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
        return matchedCommands
    }
}