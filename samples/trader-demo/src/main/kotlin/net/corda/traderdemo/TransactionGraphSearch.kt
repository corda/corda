/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.traderdemo

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.TransactionStorage
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import java.util.*
import java.util.concurrent.Callable

/**
 * Given a map of transaction id to [SignedTransaction], performs a breadth first search of the dependency graph from
 * the starting point down in order to find transactions that match the given query criteria.
 *
 * Currently, only one kind of query is supported: find any transaction that contains a command of the given type.
 *
 * In future, this should support restricting the search by time, and other types of useful query.
 *
 * @property transactions map of transaction id to [SignedTransaction].
 * @property startPoints transactions to use as starting points for the search.
 * @property query query to test transactions within the graph for matching.
 */
class TransactionGraphSearch(private val transactions: TransactionStorage,
                             private val startPoints: List<WireTransaction>,
                             private val query: Query) : Callable<List<WireTransaction>> {
    /**
     * Query criteria to match transactions against.
     *
     * @property withCommandOfType contract command class to restrict matches to, or null for no filtering by command. Matches the class or
     * any subclass.
     * @property followInputsOfType contract output state class to follow the corresponding inputs to. Matches this exact class only.
     */
    data class Query(
            val withCommandOfType: Class<out CommandData>? = null,
            val followInputsOfType: Class<out ContractState>? = null
    ) {
        /**
         * Test if the given transaction matches this query. Currently only supports checking if the transaction that
         * contains a command of the given type.
         */
        fun matches(tx: WireTransaction): Boolean {
            if (withCommandOfType != null) {
                if (tx.commands.any { it.value.javaClass.isAssignableFrom(withCommandOfType) })
                    return true
            }
            return false
        }
    }

    override fun call(): List<WireTransaction> {
        val alreadyVisited = HashSet<SecureHash>()
        val next = ArrayList<WireTransaction>(startPoints)

        val results = ArrayList<WireTransaction>()

        while (next.isNotEmpty()) {
            val tx = next.removeAt(next.lastIndex)

            if (query.matches(tx))
                results += tx

            val inputsLeadingToUnvisitedTx: Iterable<StateRef> = tx.inputs.filter { it.txhash !in alreadyVisited }
            val unvisitedInputTxs: Map<SecureHash, SignedTransaction> = inputsLeadingToUnvisitedTx.map { it.txhash }.toHashSet().mapNotNull { transactions.getTransaction(it) }.associateBy { it.id }
            val unvisitedInputTxsWithInputIndex: Iterable<Pair<SignedTransaction, Int>> = inputsLeadingToUnvisitedTx.filter { it.txhash in unvisitedInputTxs.keys }.map { Pair(unvisitedInputTxs[it.txhash]!!, it.index) }
            next += (unvisitedInputTxsWithInputIndex.filter { (stx, idx) ->
                query.followInputsOfType == null || stx.tx.outputs[idx].data.javaClass == query.followInputsOfType
            }.map { it.first }.filter { stx -> alreadyVisited.add(stx.id) }.map { it.tx })
        }

        return results
    }
}
