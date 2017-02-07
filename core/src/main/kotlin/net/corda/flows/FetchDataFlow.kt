package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.NamedByHash
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.flows.FetchDataFlow.DownloadedVsRequestedDataMismatch
import net.corda.flows.FetchDataFlow.HashNotFound
import java.util.*

/**
 * An abstract flow for fetching typed data from a remote peer.
 *
 * Given a set of hashes (IDs), either loads them from local disk or asks the remote peer to provide them.
 *
 * A malicious response in which the data provided by the remote peer does not hash to the requested hash results in
 * [DownloadedVsRequestedDataMismatch] being thrown. If the remote peer doesn't have an entry, it results in a
 * [HashNotFound] exception being thrown.
 *
 * By default this class does not insert data into any local database, if you want to do that after missing items were
 * fetched then override [maybeWriteToDisk]. You *must* override [load]. If the wire type is not the same as the
 * ultimate type, you must also override [convert].
 *
 * @param T The ultimate type of the data being fetched.
 * @param W The wire type of the data being fetched, for when it isn't the same as the ultimate type.
 */
abstract class FetchDataFlow<T : NamedByHash, in W : Any>(
        protected val requests: Set<SecureHash>,
        protected val otherSide: Party) : FlowLogic<FetchDataFlow.Result<T>>() {

    class DownloadedVsRequestedDataMismatch(val requested: SecureHash, val got: SecureHash) : IllegalArgumentException()
    class DownloadedVsRequestedSizeMismatch(val requested: Int, val got: Int) : IllegalArgumentException()
    class HashNotFound(val requested: SecureHash) : FlowException()

    data class Request(val hashes: List<SecureHash>)
    data class Result<out T : NamedByHash>(val fromDisk: List<T>, val downloaded: List<T>)

    @Suspendable
    @Throws(HashNotFound::class)
    override fun call(): Result<T> {
        // Load the items we have from disk and figure out which we're missing.
        val (fromDisk, toFetch) = loadWhatWeHave()

        return if (toFetch.isEmpty()) {
            Result(fromDisk, emptyList())
        } else {
            logger.trace("Requesting ${toFetch.size} dependency(s) for verification")

            // TODO: Support "large message" response streaming so response sizes are not limited by RAM.
            val maybeItems = sendAndReceive<ArrayList<W>>(otherSide, Request(toFetch))
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

    private fun validateFetchResponse(maybeItems: UntrustworthyData<ArrayList<W>>,
                                      requests: List<SecureHash>): List<T> {
        return maybeItems.unwrap { response ->
            if (response.size != requests.size)
                throw DownloadedVsRequestedSizeMismatch(requests.size, response.size)
            val answers = response.map { convert(it) }
            // Check transactions actually hash to what we requested, if this fails the remote node
            // is a malicious flow violator or buggy.
            for ((index, item) in answers.withIndex()) {
                if (item.id != requests[index])
                    throw DownloadedVsRequestedDataMismatch(requests[index], item.id)
            }
            answers
        }
    }
}
