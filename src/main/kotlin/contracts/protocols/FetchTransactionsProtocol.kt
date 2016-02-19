/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts.protocols

import co.paralleluniverse.fibers.Suspendable
import core.SignedTransaction
import core.crypto.SecureHash
import core.protocols.ProtocolLogic
import core.messaging.SingleMessageRecipient
import core.utilities.UntrustworthyData
import core.node.DataVendingService
import core.random63BitValue
import java.util.*

/**
 * Given a set of tx hashes (IDs), either loads them from local disk or asks the remote peer to provide them.
 * A malicious response in which the data provided by the remote peer does not hash to the requested hash results in
 * [DownloadedVsRequestedDataMismatch] being thrown. If the remote peer doesn't have an entry, it results in a
 * HashNotFound. Note that returned transactions are not inserted into the database, because it's up to the caller
 * to actually verify the transactions are valid.
 */
class FetchTransactionsProtocol(private val requests: Set<SecureHash>,
                                private val otherSide: SingleMessageRecipient) : ProtocolLogic<FetchTransactionsProtocol.Result>() {

    data class Result(val fromDisk: List<SignedTransaction>, val downloaded: List<SignedTransaction>)

    @Suspendable
    override fun call(): Result {
        val sid = random63BitValue()

        // Load the transactions we have from disk and figure out which we're missing.
        val (fromDisk, toFetch) = loadWhatWeHave()

        return if (toFetch.isEmpty()) {
            Result(fromDisk, emptyList())
        } else {
            logger.trace("Requesting ${toFetch.size} dependency(s) for verification")

            val fetchReq = DataVendingService.Request(toFetch, serviceHub.networkService.myAddress, sid)
            val maybeTxns = sendAndReceive<ArrayList<SignedTransaction?>>("platform.fetch.tx", otherSide, 0, sid, fetchReq)
            // Check for a buggy/malicious peer answering with something that we didn't ask for.
            // Note that we strip the UntrustworthyData marker here, but of course the returned transactions may be
            // invalid in other ways! Perhaps we should keep it.
            Result(fromDisk, validateTXFetchResponse(maybeTxns, toFetch))
        }
    }

    private fun loadWhatWeHave(): Pair<List<SignedTransaction>, List<SecureHash>> {
        val fromDisk = ArrayList<SignedTransaction>()
        val toFetch = ArrayList<SecureHash>()
        for (txid in requests) {
            val stx = serviceHub.storageService.validatedTransactions[txid]
            if (stx == null)
                toFetch += txid
            else
                fromDisk += stx
        }
        return Pair(fromDisk, toFetch)
    }

    private fun validateTXFetchResponse(maybeTxns: UntrustworthyData<ArrayList<SignedTransaction?>>,
                                        requests: List<SecureHash>): List<SignedTransaction> =
            maybeTxns.validate { response ->
                if (response.size != requests.size)
                    throw BadAnswer()
                for ((index, resp) in response.withIndex()) {
                    if (resp == null) throw HashNotFound(requests[index])
                }
                val answers = response.requireNoNulls()
                // Check transactions actually hash to what we requested, if this fails the remote node
                // is a malicious protocol violator or buggy.
                for ((index, stx) in answers.withIndex())
                    if (stx.id != requests[index])
                        throw DownloadedVsRequestedDataMismatch(requests[index], stx.id)

                answers
            }

    open class BadAnswer : Exception()
    class HashNotFound(val requested: SecureHash) : BadAnswer()
    class DownloadedVsRequestedDataMismatch(val requested: SecureHash, val got: SecureHash) : BadAnswer()
}

