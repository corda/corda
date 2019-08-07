package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.internal.FetchTransactionsFlow
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.TransactionsResolver
import net.corda.core.internal.dependencies
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.debug
import java.util.*

class InMemoryTransactionsResolver(private val flow: ResolveTransactionsFlow) : TransactionsResolver {
    companion object {
        /** The maximum number of transactions this flow will try to download before bailing out. */
        var transactionCountLimit = 5000
            set(value) {
                require(value > 0) { "$value is not a valid count limit" }
                field = value
            }
    }

    private var sortedDependencies: List<SignedTransaction>? = null
    private val logger = flow.logger

    @Suspendable
    override fun downloadDependencies() {
        // Maintain a work queue of all hashes to load/download, initialised with our starting set. Then do a breadth
        // first traversal across the dependency graph.
        //
        // TODO: This approach has two problems. Analyze and resolve them:
        //
        // (1) This flow leaks private data. If you download a transaction and then do NOT request a
        // dependency, it means you already have it, which in turn means you must have been involved with it before
        // somehow, either in the tx itself or in any following spend of it. If there were no following spends, then
        // your peer knows for sure that you were involved ... this is bad! The only obvious ways to fix this are
        // something like onion routing of requests, secure hardware, or both.
        //
        // (2) If the identity service changes the assumed identity of one of the public keys, it's possible
        // that the "tx in db is valid" invariant is violated if one of the contracts checks the identity! Should
        // the db contain the identities that were resolved when the transaction was first checked, or should we
        // accept this kind of change is possible? Most likely solution is for identity data to be an attachment.

        logger.debug { "Downloading dependencies for transactions ${flow.txHashes}" }
        val nextRequests = LinkedHashSet<SecureHash>(flow.txHashes)   // Keep things unique but ordered, for unit test stability.
        val topologicalSort = TopologicalSort()
        val seenIds = HashSet<SecureHash>()

        while (nextRequests.isNotEmpty()) {
            // Don't re-download the same tx when we haven't verified it yet but it's referenced multiple times in the
            // graph we're traversing.
            nextRequests.removeAll(seenIds)
            if (nextRequests.isEmpty()) {
                // Done early.
                break
            }

            // Request the standalone transaction data (which may refer to things we don't yet have).
            val requestedTxs = flow.subFlow(FetchTransactionsFlow(nextRequests, flow.otherSide))
            val freshDownloads = requestedTxs.downloaded
            val existingTxs = requestedTxs.fromDisk

            for (downloaded in freshDownloads) {
                require(seenIds.add(downloaded.id)) { "Transaction ID ${downloaded.id} already seen" }
                if (seenIds.size > transactionCountLimit) {
                    throw ExcessivelyLargeTransactionGraph()
                }

                val dependencies = downloaded.dependencies
                topologicalSort.add(downloaded, dependencies)

                flow.fetchMissingAttachments(downloaded)
                flow.fetchMissingNetworkParameters(downloaded)

                // Add all input states and reference input states to the work queue.
                nextRequests.addAll(dependencies)
            }

            // It's possible that the node has a transaction in storage already. Dependencies should also be present for this transaction,
            // so just remove these IDs from the set of next requests.
            nextRequests.removeAll(existingTxs.map { it.id })
        }

        sortedDependencies = topologicalSort.complete()
        logger.debug { "Downloaded ${sortedDependencies?.size ?: 0} dependencies from remote peer for transactions ${flow.txHashes}" }
    }

    override fun recordDependencies(usedStatesToRecord: StatesToRecord) {
        logger.debug { "Recording ${this.sortedDependencies?.size ?: 0} dependencies for ${flow.txHashes.size} transactions" }
        for (tx in checkNotNull(sortedDependencies)) {
            // For each transaction, verify it and insert it into the database. As we are iterating over them in a
            // depth-first order, we should not encounter any verification failures due to missing data. If we fail
            // half way through, it's no big deal, although it might result in us attempting to re-download data
            // redundantly next time we attempt verification.
            tx.verify(flow.serviceHub)
            flow.serviceHub.recordTransactions(usedStatesToRecord, listOf(tx))
        }
    }

    class ExcessivelyLargeTransactionGraph : FlowException()

    /**
     * Provides a way to topologically sort SignedTransactions. This means that given any two transactions T1 and T2 in the
     * list returned by [complete] if T1 is a dependency of T2 then T1 will occur earlier than T2.
     */
    class TopologicalSort {
        private val forwardGraph = HashMap<SecureHash, MutableSet<SignedTransaction>>()
        private val transactions = ArrayList<SignedTransaction>()

        /**
         * Add a transaction to the to-be-sorted set of transactions.
         */
        fun add(stx: SignedTransaction, dependencies: Set<SecureHash>) {
            dependencies.forEach {
                // Note that we use a LinkedHashSet here to make the traversal deterministic (as long as the input list is).
                forwardGraph.computeIfAbsent(it) { LinkedHashSet() }.add(stx)
            }
            transactions += stx
        }

        /**
         * Return the sorted list of signed transactions.
         */
        fun complete(): List<SignedTransaction> {
            val visited = HashSet<SecureHash>(transactions.size)
            val result = ArrayList<SignedTransaction>(transactions.size)

            fun visit(transaction: SignedTransaction) {
                if (visited.add(transaction.id)) {
                    forwardGraph[transaction.id]?.forEach(::visit)
                    result += transaction
                }
            }

            transactions.forEach(::visit)

            return result.apply(Collections::reverse)
        }
    }
}
