package net.corda.core.internal

import net.corda.core.crypto.SecureHash
import java.util.*
import java.util.Collections.reverse

/**
 * Provides a way to topologically sort SignedTransactions represented just their [SecureHash] IDs. This means that given any two transactions
 * T1 and T2 in the list returned by [complete] if T1 is a dependency of T2 then T1 will occur earlier than T2.
 */
class TopologicalSort {
    private val forwardGraph = HashMap<SecureHash, MutableSet<SecureHash>>()
    private val transactionIds = LinkedHashSet<SecureHash>()

    val seenTransactionIds: Set<SecureHash> get() = Collections.unmodifiableSet(transactionIds)

    /**
     * Add a transaction to the to-be-sorted set of transactions.
     * @param txId The ID of the transaction.
     * @param dependentIds the IDs of all the transactions [txId] depends on.
     */
    fun add(txId: SecureHash, dependentIds: Set<SecureHash>) {
        require(transactionIds.add(txId)) { "Transaction ID $txId already seen" }
        dependentIds.forEach {
            // Note that we use a LinkedHashSet here to make the traversal deterministic (as long as the input list is).
            forwardGraph.computeIfAbsent(it) { LinkedHashSet() }.add(txId)
        }
    }

    /**
     * Return the sorted list of transaction IDs.
     */
    fun complete(): List<SecureHash> {
        val visited = HashSet<SecureHash>(transactionIds.size)
        val result = ArrayList<SecureHash>(transactionIds.size)

        fun visit(txId: SecureHash) {
            if (visited.add(txId)) {
                forwardGraph[txId]?.forEach(::visit)
                result += txId
            }
        }

        transactionIds.forEach(::visit)

        return result.apply(::reverse)
    }
}
