package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.exactAdd
import java.util.*

// TODO: This code is currently unit tested by TwoPartyTradeFlowTests, it should have its own tests.
/**
 * Resolves transactions for the specified [txHashes] along with their full history (dependency graph) from [otherSide].
 * Each retrieved transaction is validated and inserted into the local transaction storage.
 *
 * @return a list of verified [SignedTransaction] objects, in a depth-first order.
 */
class ResolveTransactionsFlow(private val txHashes: Set<SecureHash>,
                              private val otherSide: FlowSession) : FlowLogic<List<SignedTransaction>>() {
    /**
     * Resolves and validates the dependencies of the specified [SignedTransaction]. Fetches the attachments, but does
     * *not* validate or store the [SignedTransaction] itself.
     *
     * @return a list of verified [SignedTransaction] objects, in a depth-first order.
     */
    constructor(signedTransaction: SignedTransaction, otherSide: FlowSession) : this(dependencyIDs(signedTransaction), otherSide) {
        this.signedTransaction = signedTransaction
    }

    companion object {

        /**
         * Request unspendable inputs as well as regular inputs.
         */
        private fun dependencyIDs(stx: SignedTransaction): Set<SecureHash> {
            return stx.inputs.map { it.txhash }.toSet() + stx.references.map { it.txhash }.toSet()
        }

        /**
         * Topologically sorts the given transactions such that dependencies are listed before dependers.
         */
        @JvmStatic
        fun topologicalSort(transactions: Collection<SignedTransaction>): List<SignedTransaction> {
            // Construct txhash -> dependent-txs map
            val forwardGraph = HashMap<SecureHash, HashSet<SignedTransaction>>()
            transactions.forEach { stx ->
                stx.inputs.forEach { (txhash) ->
                    // Note that we use a LinkedHashSet here to make the traversal deterministic (as long as the input list is)
                    forwardGraph.getOrPut(txhash) { LinkedHashSet() }.add(stx)
                }
            }

            val visited = HashSet<SecureHash>(transactions.size)
            val result = ArrayList<SignedTransaction>(transactions.size)

            fun visit(transaction: SignedTransaction) {
                if (transaction.id !in visited) {
                    visited.add(transaction.id)
                    forwardGraph[transaction.id]?.forEach(::visit)
                    result.add(transaction)
                }
            }

            transactions.forEach(::visit)

            result.reverse()
            require(result.size == transactions.size)
            return result
        }
    }

    @CordaSerializable
    class ExcessivelyLargeTransactionGraph : FlowException()

    /** Transaction for fetch attachments for */
    private var signedTransaction: SignedTransaction? = null

    // TODO: Figure out a more appropriate DOS limit here, 5000 is simply a very bad guess.
    /** The maximum number of transactions this flow will try to download before bailing out. */
    var transactionCountLimit = 5000
        set(value) {
            require(value > 0) { "$value is not a valid count limit" }
            field = value
        }

    @Suspendable
    @Throws(FetchDataFlow.HashNotFound::class)
    override fun call(): List<SignedTransaction> {
        // Start fetching data.
        val newTxns = downloadDependencies(txHashes)
        fetchMissingAttachments(signedTransaction?.let { newTxns + it } ?: newTxns)
        otherSide.send(FetchDataFlow.Request.End)
        // Finish fetching data.

        val result = topologicalSort(newTxns)
        result.forEach {
            // For each transaction, verify it and insert it into the database. As we are iterating over them in a
            // depth-first order, we should not encounter any verification failures due to missing data. If we fail
            // half way through, it's no big deal, although it might result in us attempting to re-download data
            // redundantly next time we attempt verification.
            it.verify(serviceHub)
            serviceHub.recordTransactions(StatesToRecord.NONE, listOf(it))
        }

        return signedTransaction?.let {
            result + it
        } ?: result
    }

    @Suspendable
    private fun downloadDependencies(depsToCheck: Set<SecureHash>): List<SignedTransaction> {
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

        val nextRequests = LinkedHashSet<SecureHash>()   // Keep things unique but ordered, for unit test stability.
        nextRequests.addAll(depsToCheck)
        val resultQ = LinkedHashMap<SecureHash, SignedTransaction>()

        val limit = transactionCountLimit
        var limitCounter = 0
        while (nextRequests.isNotEmpty()) {
            // Don't re-download the same tx when we haven't verified it yet but it's referenced multiple times in the
            // graph we're traversing.
            val notAlreadyFetched = nextRequests.filterNot { it in resultQ }.toSet()
            nextRequests.clear()

            if (notAlreadyFetched.isEmpty())   // Done early.
                break

            // Request the standalone transaction data (which may refer to things we don't yet have).
            val downloads: List<SignedTransaction> = subFlow(FetchTransactionsFlow(notAlreadyFetched, otherSide)).downloaded

            for (stx in downloads)
                check(resultQ.putIfAbsent(stx.id, stx) == null)   // Assert checks the filter at the start.

            // Add all input states to the work queue.
            val inputHashes = downloads.flatMap { it.inputs }.map { it.txhash }
            nextRequests.addAll(inputHashes)

            limitCounter = limitCounter exactAdd nextRequests.size
            if (limitCounter > limit)
                throw ExcessivelyLargeTransactionGraph()
        }
        return resultQ.values.toList()
    }

    /**
     * Returns a list of all the dependencies of the given transactions, deepest first i.e. the last downloaded comes
     * first in the returned list and thus doesn't have any unverified dependencies.
     */
    // TODO: This could be done in parallel with other fetches for extra speed.
    @Suspendable
    private fun fetchMissingAttachments(downloads: List<SignedTransaction>) {
        val attachments = downloads.map(SignedTransaction::coreTransaction).flatMap { tx ->
            when (tx) {
                is WireTransaction -> tx.attachments
                is ContractUpgradeWireTransaction -> listOf(tx.legacyContractAttachmentId, tx.upgradedContractAttachmentId)
                else -> emptyList()
            }
        }
        val missingAttachments = attachments.filter { serviceHub.attachments.openAttachment(it) == null }
        if (missingAttachments.isNotEmpty())
            subFlow(FetchAttachmentsFlow(missingAttachments.toSet(), otherSide))
    }
}
