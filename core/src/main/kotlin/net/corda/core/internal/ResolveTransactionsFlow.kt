package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.debug

/**
 * Resolves transactions for the specified [txHashes] along with their full history (dependency graph) from [otherSide].
 * Each retrieved transaction is validated and inserted into the local transaction storage.
 */
@DeleteForDJVM
class ResolveTransactionsFlow private constructor(
        private val initialTx: SignedTransaction?,
        private val txHashes: Set<SecureHash>,
        private val otherSide: FlowSession,
        private val statesToRecord: StatesToRecord
) : FlowLogic<Unit>() {

    constructor(txHashes: Set<SecureHash>, otherSide: FlowSession, statesToRecord: StatesToRecord = StatesToRecord.NONE)
            : this(null, txHashes, otherSide, statesToRecord)

    /**
     * Resolves and validates the dependencies of the specified [SignedTransaction]. Fetches the attachments, but does
     * *not* validate or store the [SignedTransaction] itself.
     *
     * @return a list of verified [SignedTransaction] objects, in a depth-first order.
     */
    constructor(transaction: SignedTransaction, otherSide: FlowSession, statesToRecord: StatesToRecord = StatesToRecord.NONE)
            : this(transaction, transaction.dependencies, otherSide, statesToRecord)

    private companion object {
        private val MAX_CHECKPOINT_RESOLUTION = Integer.getInteger("${ResolveTransactionsFlow::class.java.name}.max-checkpoint-resolution", 0)

        private val SignedTransaction.dependencies: Set<SecureHash>
            get() = (inputs.asSequence() + references.asSequence()).map { it.txhash }.toSet()
    }

    private var fetchNetParamsFromCounterpart = false

    @Suspendable
    override fun call() {
        // TODO This error should actually case the flow to be sent to the flow hospital to be retried
        val counterpartyPlatformVersion = checkNotNull(serviceHub.networkMapCache.getNodeByLegalIdentity(otherSide.counterparty)?.platformVersion) {
            "Couldn't retrieve party's ${otherSide.counterparty} platform version from NetworkMapCache"
        }
        // Fetch missing parameters flow was added in version 4. This check is needed so we don't end up with node V4 sending parameters
        // request to node V3 that doesn't know about this protocol.
        fetchNetParamsFromCounterpart = counterpartyPlatformVersion >= 4

        if (initialTx != null) {
            fetchMissingAttachments(initialTx)
            fetchMissingNetworkParameters(initialTx)
        }

        val (newTxIdsSorted, newTxs) = downloadDependencies()

        otherSide.send(FetchDataFlow.Request.End)
        // Finish fetching data.

        logger.debug { "Downloaded transaction dependencies of ${newTxIdsSorted.size} transactions" }

        // If transaction resolution is performed for a transaction where some states are relevant, then those should be
        // recorded if this has not already occurred.
        val usedStatesToRecord = if (statesToRecord == StatesToRecord.NONE) StatesToRecord.ONLY_RELEVANT else statesToRecord
        if (newTxs != null) {
            for (txId in newTxIdsSorted) {
                val tx = newTxs.getValue(txId)
                // For each transaction, verify it and insert it into the database. As we are iterating over them in a
                // depth-first order, we should not encounter any verification failures due to missing data. If we fail
                // half way through, it's no big deal, although it might result in us attempting to re-download data
                // redundantly next time we attempt verification.
                tx.verify(serviceHub)
                serviceHub.recordTransactions(usedStatesToRecord, listOf(tx))
            }
        } else {
            val transactionStorage = serviceHub.validatedTransactions as WritableTransactionStorage
            for (txId in newTxIdsSorted) {
                // Retrieve and delete the transaction from the unverified store.
                val (tx, isVerified) = checkNotNull(transactionStorage.getTransactionInternal(txId)) {
                    "Somehow the unverified transaction ($txId) that we stored previously is no longer there."
                }
                if (!isVerified) {
                    tx.verify(serviceHub)
                    serviceHub.recordTransactions(usedStatesToRecord, listOf(tx))
                } else {
                    logger.debug { "No need to record $txId as it's already been verified" }
                }
            }
        }
    }

    @Suspendable
    private fun downloadDependencies(): Pair<List<SecureHash>, Map<SecureHash, SignedTransaction>?> {
        val transactionStorage = serviceHub.validatedTransactions as WritableTransactionStorage

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

        val nextRequests = LinkedHashSet<SecureHash>(txHashes)   // Keep things unique but ordered, for unit test stability.
        val topologicalSort = TopologicalSort()
        var downloadedTxs: MutableMap<SecureHash, SignedTransaction>? = HashMap()

        while (nextRequests.isNotEmpty()) {
            // Don't re-download the same tx when we haven't verified it yet but it's referenced multiple times in the
            // graph we're traversing.
            nextRequests.removeAll(topologicalSort.seenTransactionIds)
            if (nextRequests.isEmpty()) {
                // Done early.
                break
            }

            // Request the standalone transaction data (which may refer to things we don't yet have).
            val freshDownloads = subFlow(FetchTransactionsFlow(nextRequests, otherSide)).downloaded

            for (downloaded in freshDownloads) {
                val dependencies = downloaded.dependencies
                topologicalSort.add(downloaded.id, dependencies)
                // Add all input states and reference input states to the work queue.
                nextRequests.addAll(dependencies)

                if (downloadedTxs != null) {
                    if (downloadedTxs.size < MAX_CHECKPOINT_RESOLUTION) {
                        downloadedTxs[downloaded.id] = downloaded
                    } else {
                        logger.info("Resolving transaction dependencies has reached a checkpoint limit of $MAX_CHECKPOINT_RESOLUTION " +
                                "transactions. Switching to the node database for storing the unverified transactions.")
                        downloadedTxs.values.forEach(transactionStorage::addUnverifiedTransaction)
                        // This acts as both a flag that we've switched over to storing the backchain into the db, and to remove what's been
                        // built up in the checkpoint
                        downloadedTxs = null
                        transactionStorage.addUnverifiedTransaction(downloaded)
                    }
                } else {
                    transactionStorage.addUnverifiedTransaction(downloaded)
                }

                fetchMissingAttachments(downloaded)
                fetchMissingNetworkParameters(downloaded)
            }
        }

        return Pair(topologicalSort.complete(), downloadedTxs)
    }

    /**
     * Returns a list of all the dependencies of the given transactions, deepest first i.e. the last downloaded comes
     * first in the returned list and thus doesn't have any unverified dependencies.
     */
    // TODO: This could be done in parallel with other fetches for extra speed.
    @Suspendable
    private fun fetchMissingAttachments(transaction: SignedTransaction) {
        val tx = transaction.coreTransaction
        val attachmentIds = when (tx) {
            is WireTransaction -> tx.attachments.toSet()
            is ContractUpgradeWireTransaction -> setOf(tx.legacyContractAttachmentId, tx.upgradedContractAttachmentId)
            else -> return
        }
        subFlow(FetchAttachmentsFlow(attachmentIds, otherSide))
    }

    // TODO This can also be done in parallel. See comment to [fetchMissingAttachments] above.
    @Suspendable
    private fun fetchMissingNetworkParameters(transaction: SignedTransaction) {
        if (fetchNetParamsFromCounterpart) {
            transaction.networkParametersHash?.let {
                subFlow(FetchNetworkParametersFlow(setOf(it), otherSide))
            }
        }
    }
}
