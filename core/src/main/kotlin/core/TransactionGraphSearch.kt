/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import core.crypto.SecureHash
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
 * TODO: Write unit tests for this.
 */
class TransactionGraphSearch(val transactions: Map<SecureHash, SignedTransaction>,
                             val startPoints: List<WireTransaction>) : Callable<List<WireTransaction>> {
    class Query(
        val withCommandOfType: Class<out CommandData>? = null
    )

    var query: Query = Query()

    override fun call(): List<WireTransaction> {
        val q = query

        val next = ArrayList<SecureHash>()
        next += startPoints.flatMap { it.inputs.map { it.txhash } }

        val results = ArrayList<WireTransaction>()

        while (next.isNotEmpty()) {
            val hash = next.removeAt(next.lastIndex)
            val tx = transactions[hash]?.tx ?: continue

            if (q.matches(tx))
                results += tx

            next += tx.inputs.map { it.txhash }
        }

        return results
    }

    private fun Query.matches(tx: WireTransaction): Boolean {
        if (withCommandOfType != null) {
            if (tx.commands.any { it.data.javaClass.isAssignableFrom(withCommandOfType) })
                return true
        }
        return false
    }
}
