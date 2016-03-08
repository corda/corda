/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package protocols

import co.paralleluniverse.fibers.Suspendable
import core.NamedByHash
import core.crypto.SecureHash
import core.messaging.SingleMessageRecipient
import core.node.services.DataVendingService
import core.protocols.ProtocolLogic
import core.random63BitValue
import core.utilities.UntrustworthyData
import java.util.*

/**
 * An abstract protocol for fetching typed data from a remote peer.
 *
 * Given a set of hashes (IDs), either loads them from local disk or asks the remote peer to provide them.
 *
 * A malicious response in which the data provided by the remote peer does not hash to the requested hash results in
 * [DownloadedVsRequestedDataMismatch] being thrown. If the remote peer doesn't have an entry, it results in a
 * [HashNotFound] exception being thrown.
 *
 * By default this class does not insert data into any local database, if you want to do that after missing items were
 * fetched then override [maybeWriteToDisk]. You *must* override [load] and [queryTopic]. If the wire type is not the
 * same as the ultimate type, you must also override [convert].
 *
 * @param T The ultimate type of the data being fetched.
 * @param W The wire type of the data being fetched, for when it isn't the same as the ultimate type.
 */
abstract class FetchDataProtocol<T : NamedByHash, W : Any>(
        protected val requests: Set<SecureHash>,
        protected val otherSide: SingleMessageRecipient) : ProtocolLogic<FetchDataProtocol.Result<T>>() {

    open class BadAnswer : Exception()
    class HashNotFound(val requested: SecureHash) : BadAnswer()
    class DownloadedVsRequestedDataMismatch(val requested: SecureHash, val got: SecureHash) : BadAnswer()

    data class Result<T : NamedByHash>(val fromDisk: List<T>, val downloaded: List<T>)

    protected abstract val queryTopic: String

    @Suspendable
    override fun call(): Result<T> {
        // Load the items we have from disk and figure out which we're missing.
        val (fromDisk, toFetch) = loadWhatWeHave()

        return if (toFetch.isEmpty()) {
            Result(fromDisk, emptyList())
        } else {
            logger.trace("Requesting ${toFetch.size} dependency(s) for verification")

            val sid = random63BitValue()
            val fetchReq = DataVendingService.Request(toFetch, serviceHub.networkService.myAddress, sid)
            // TODO: Support "large message" response streaming so response sizes are not limited by RAM.
            val maybeItems = sendAndReceive<ArrayList<W?>>(queryTopic, otherSide, 0, sid, fetchReq)
            // Check for a buggy/malicious peer answering with something that we didn't ask for.
            val downloaded = validateFetchResponse(maybeItems, toFetch)
            maybeWriteToDisk(downloaded)
            Result(fromDisk, downloaded)
        }
    }

    protected open fun maybeWriteToDisk(downloaded: List<T>) {
        // Do nothing by default.
    }

    private fun loadWhatWeHave(): Pair<List<T>, List<SecureHash>> {
        val fromDisk = ArrayList<T>()
        val toFetch = ArrayList<SecureHash>()
        for (txid in requests) {
            val stx = load(txid)
            if (stx == null)
                toFetch += txid
            else
                fromDisk += stx
        }
        return Pair(fromDisk, toFetch)
    }

    protected abstract fun load(txid: SecureHash): T?

    @Suppress("UNCHECKED_CAST")
    protected open fun convert(wire: W): T = wire as T

    private fun validateFetchResponse(maybeItems: UntrustworthyData<ArrayList<W?>>,
                                      requests: List<SecureHash>): List<T> =
            maybeItems.validate { response ->
                if (response.size != requests.size)
                    throw BadAnswer()
                for ((index, resp) in response.withIndex()) {
                    if (resp == null) throw HashNotFound(requests[index])
                }
                val answers = response.requireNoNulls().map { convert(it) }
                // Check transactions actually hash to what we requested, if this fails the remote node
                // is a malicious protocol violator or buggy.
                for ((index, item) in answers.withIndex())
                    if (item.id != requests[index])
                        throw DownloadedVsRequestedDataMismatch(requests[index], item.id)

                answers
            }
}