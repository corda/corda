package net.corda.core.contracts

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
 * @param transactions map of transaction id to [SignedTransaction].
 * @param startPoints transactions to use as starting points for the search.
 */
class TransactionGraphSearch(val transactions: TransactionStorage,
                             val startPoints: List<WireTransaction>) : Callable<List<WireTransaction>> {
    class Query(
            val withCommandOfType: Class<out CommandData>? = null,
            val followInputsOfType: Class<out ContractState>? = null
    )

    var query: Query = Query()

    override fun call(): List<WireTransaction> {
        val q = query

        val alreadyVisited = HashSet<SecureHash>()
        val next = ArrayList<WireTransaction>(startPoints)

        val results = ArrayList<WireTransaction>()

        while (next.isNotEmpty()) {
            val tx = next.removeAt(next.lastIndex)

            if (q.matches(tx))
                results += tx

            val inputsLeadingToUnvisitedTx: Iterable<StateRef> = tx.inputs.filter { it.txhash !in alreadyVisited }
            val unvisitedInputTxs: Map<SecureHash, SignedTransaction> = inputsLeadingToUnvisitedTx.map { it.txhash }.toHashSet().map { transactions.getTransaction(it) }.filterNotNull().associateBy { it.id }
            val unvisitedInputTxsWithInputIndex: Iterable<Pair<SignedTransaction, Int>> = inputsLeadingToUnvisitedTx.filter { it.txhash in unvisitedInputTxs.keys }.map { Pair(unvisitedInputTxs[it.txhash]!!, it.index) }
            next += (unvisitedInputTxsWithInputIndex.filter { q.followInputsOfType == null || it.first.tx.outputs[it.second].data.javaClass == q.followInputsOfType }
                    .map { it.first }.filter { alreadyVisited.add(it.id) }.map { it.tx })
        }

        return results
    }

    private fun Query.matches(tx: WireTransaction): Boolean {
        if (withCommandOfType != null) {
            if (tx.commands.any { it.value.javaClass.isAssignableFrom(withCommandOfType) })
                return true
        }
        return false
    }
}
