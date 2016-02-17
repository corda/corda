/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.protocols

import co.paralleluniverse.fibers.Suspendable
import core.LedgerTransaction
import core.SignedTransaction
import core.TransactionGroup
import core.WireTransaction
import core.crypto.SecureHash
import core.messaging.ProtocolLogic
import core.messaging.SingleMessageRecipient
import java.util.*

/**
 * This protocol fetches each transaction identified by the given hashes from either disk or network, along with all
 * their dependencies, and verifies them together using a single [TransactionGroup]. If no exception is thrown, then
 * all the transactions have been successfully verified and inserted into the local database.
 *
 * A couple of constructors are provided that accept a single transaction. When these are used, the dependencies of that
 * transaction are resolved and then the transaction itself is verified. Again, if successful, the results are inserted
 * into the database as long as a [SignedTransaction] was provided. If only the [WireTransaction] form was provided
 * then this isn't enough to put into the local database, so only the dependencies are inserted. This way to use the
 * protocol is helpful when resolving and verifying a finished but partially signed transaction.
 */
class ResolveTransactionsProtocol(private val txHashes: Set<SecureHash>,
                                  private val otherSide: SingleMessageRecipient) : ProtocolLogic<Unit>() {

    companion object {
        private fun dependencyIDs(wtx: WireTransaction) = wtx.inputs.map { it.txhash }.toSet()
    }

    // Transactions to verify after the dependencies.
    private var stx: SignedTransaction? = null
    private var wtx: WireTransaction? = null

    constructor(stx: SignedTransaction, otherSide: SingleMessageRecipient) : this(stx.tx, otherSide) {
        this.stx = stx
    }

    constructor(wtx: WireTransaction, otherSide: SingleMessageRecipient) : this(dependencyIDs(wtx), otherSide) {
        this.wtx = wtx
    }

    @Suspendable
    override fun call(): Unit {
        val toVerify = HashSet<LedgerTransaction>()
        val alreadyVerified = HashSet<LedgerTransaction>()
        val downloadedSignedTxns = ArrayList<SignedTransaction>()

        // This fills out toVerify, alreadyVerified (roots) and downloadedSignedTxns.
        fetchDependenciesAndCheckSignatures(txHashes, toVerify, alreadyVerified, downloadedSignedTxns)

        if (stx != null) {
            // Check the signatures on the stx first.
            toVerify += stx!!.verifyToLedgerTransaction(serviceHub.identityService)
        } else if (wtx != null) {
            wtx!!.toLedgerTransaction(serviceHub.identityService)
        }

        // Run all the contracts and throw an exception if any of them reject.
        TransactionGroup(toVerify, alreadyVerified).verify(serviceHub.storageService.contractPrograms)

        // Now write all the transactions we just validated back to the database for next time, including
        // signatures so we can serve up these transactions to other peers when we, in turn, send one that
        // depends on them onto another peer.
        //
        // It may seem tempting to write transactions to the database as we receive them, instead of all at once
        // here at the end. Doing it this way avoids cases where a transaction is in the database but its
        // dependencies aren't, or an unvalidated and possibly broken tx is there.
        serviceHub.storageService.validatedTransactions.putAll(downloadedSignedTxns.associateBy { it.id })
    }

    @Suspendable
    private fun fetchDependenciesAndCheckSignatures(depsToCheck: Set<SecureHash>,
                                                    toVerify: HashSet<LedgerTransaction>,
                                                    alreadyVerified: HashSet<LedgerTransaction>,
                                                    downloadedSignedTxns: ArrayList<SignedTransaction>) {
        // Maintain a work queue of all hashes to load/download, initialised with our starting set.
        // Then either fetch them from the database or request them from the other side. Look up the
        // signatures against our identity database, filtering the transactions into 'already checked'
        // and 'need to check' sets.
        //
        // TODO: This approach has two problems. Analyze and resolve them:
        //
        // (1) This protocol leaks private data. If you download a transaction and then do NOT request a
        // dependency, it means you already have it, which in turn means you must have been involved with it before
        // somehow, either in the tx itself or in any following spend of it. If there were no following spends, then
        // your peer knows for sure that you were involved ... this is bad! The only obvious ways to fix this are
        // something like onion routing of requests, secure hardware, or both.
        //
        // (2) If the identity service changes the assumed identity of one of the public keys, it's possible
        // that the "tx in db is valid" invariant is violated if one of the contracts checks the identity! Should
        // the db contain the identities that were resolved when the transaction was first checked, or should we
        // accept this kind of change is possible?

        val nextRequests = LinkedHashSet<SecureHash>()   // Keep things unique but ordered, for unit test stability.
        nextRequests.addAll(depsToCheck)

        var limitCounter = 0
        while (nextRequests.isNotEmpty()) {
            val (fromDisk, downloads) = subProtocol(FetchTransactionsProtocol(nextRequests, otherSide))
            nextRequests.clear()

            // Resolve any legal identities from known public keys in the signatures.
            val downloadedTxns = downloads.map { it.verifyToLedgerTransaction(serviceHub.identityService) }

            // Do the same for transactions loaded from disk (i.e. we checked them previously).
            val loadedTxns = fromDisk.map { it.verifyToLedgerTransaction(serviceHub.identityService) }

            toVerify.addAll(downloadedTxns)
            alreadyVerified.addAll(loadedTxns)
            downloadedSignedTxns.addAll(downloads)

            // And now add all the input states to the work queue for database or remote resolution.
            nextRequests.addAll(downloadedTxns.flatMap { it.inputs }.map { it.txhash })

            // And loop around ...
            // TODO: Figure out a more appropriate DOS limit here, 5000 is simply a guess.
            limitCounter += nextRequests.size
            if (limitCounter > 5000)
                throw TwoPartyTradeProtocol.ExcessivelyLargeTransactionGraphException()
        }
    }
}
