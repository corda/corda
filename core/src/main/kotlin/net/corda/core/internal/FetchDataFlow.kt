package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.NamedByHash
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.internal.FetchDataFlow.DownloadedVsRequestedDataMismatch
import net.corda.core.internal.FetchDataFlow.HashNotFound
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import java.nio.file.FileAlreadyExistsException
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
sealed class FetchDataFlow<T : NamedByHash, in W : Any>(
        protected val requests: Set<SecureHash>,
        protected val otherSideSession: FlowSession,
        protected val dataType: DataType) : FlowLogic<FetchDataFlow.Result<T>>() {

    @CordaSerializable
    class DownloadedVsRequestedDataMismatch(val requested: SecureHash, val got: SecureHash) : IllegalArgumentException()

    @CordaSerializable
    class DownloadedVsRequestedSizeMismatch(val requested: Int, val got: Int) : IllegalArgumentException()

    class HashNotFound(val requested: SecureHash) : FlowException()

    class IllegalTransactionRequest(val requested: SecureHash) : FlowException("Illegal attempt to request a transaction (${requested}) that is not in the transitive dependency graph of the sent transaction.")

    @CordaSerializable
    data class Result<out T : NamedByHash>(val fromDisk: List<T>, val downloaded: List<T>)

    @CordaSerializable
    sealed class Request {
        data class Data(val hashes: NonEmptySet<SecureHash>, val dataType: DataType) : Request()
        object End : Request()
    }

    @CordaSerializable
    enum class DataType {
        TRANSACTION, ATTACHMENT
    }

    @Suspendable
    @Throws(HashNotFound::class)
    override fun call(): Result<T> {
        // Load the items we have from disk and figure out which we're missing.
        val (fromDisk, toFetch) = loadWhatWeHave()

        return if (toFetch.isEmpty()) {
            Result(fromDisk, emptyList())
        } else {
            logger.info("Requesting ${toFetch.size} dependency(s) for verification from ${otherSideSession.counterparty.name}")

            // TODO: Support "large message" response streaming so response sizes are not limited by RAM.
            // We can then switch to requesting items in large batches to minimise the latency penalty.
            // This is blocked by bugs ARTEMIS-1278 and ARTEMIS-1279. For now we limit attachments and txns to 10mb each
            // and don't request items in batch, which is a performance loss, but works around the issue. We have
            // configured Artemis to not fragment messages up to 10mb so we can send 10mb messages without problems.
            // Above that, we start losing authentication data on the message fragments and take exceptions in the
            // network layer.
            val maybeItems = ArrayList<W>(toFetch.size)
            for (hash in toFetch) {
                // We skip the validation here (with unwrap { it }) because we will do it below in validateFetchResponse.
                // The only thing checked is the object type. It is a protocol violation to send results out of order.
                // TODO We need to page here after large messages will work.
                maybeItems += otherSideSession.sendAndReceive<List<W>>(Request.Data(NonEmptySet.of(hash), dataType)).unwrap { it }
            }
            // Check for a buggy/malicious peer answering with something that we didn't ask for.
            val downloaded = validateFetchResponse(UntrustworthyData(maybeItems), toFetch)
            logger.info("Fetched ${downloaded.size} elements from ${otherSideSession.counterparty.name}")
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

    protected open fun convert(wire: W): T = uncheckedCast(wire)

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

/**
 * Given a set of hashes either loads from from local storage  or requests them from the other peer. Downloaded
 * attachments are saved to local storage automatically.
 */
class FetchAttachmentsFlow(requests: Set<SecureHash>,
                           otherSide: FlowSession) : FetchDataFlow<Attachment, ByteArray>(requests, otherSide, DataType.ATTACHMENT) {

    override fun load(txid: SecureHash): Attachment? = serviceHub.attachments.openAttachment(txid)

    override fun convert(wire: ByteArray): Attachment = FetchedAttachment({ wire })

    override fun maybeWriteToDisk(downloaded: List<Attachment>) {
        for (attachment in downloaded) {
            with(serviceHub.attachments) {
                if (!hasAttachment(attachment.id)) {
                    try {
                        importAttachment(attachment.open(), "$P2P_UPLOADER:${otherSideSession.counterparty.name}", null)
                    } catch (e: FileAlreadyExistsException) {
                        // This can happen when another transaction will insert the same attachment during this transaction.
                        // The outcome is the same (the attachment is imported), so we can ignore this exception.
                        logger.debug("Attachment ${attachment.id} already inserted.")
                    }
                } else {
                    logger.debug("Attachment ${attachment.id} already exists, skipping.")
                }
            }
        }
    }

    private class FetchedAttachment(dataLoader: () -> ByteArray) : AbstractAttachment(dataLoader), SerializeAsToken {
        override val id: SecureHash by lazy { attachmentData.sha256() }

        private class Token(private val id: SecureHash) : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext) = FetchedAttachment(context.attachmentDataLoader(id))
        }

        override fun toToken(context: SerializeAsTokenContext) = Token(id)
    }
}

/**
 * Given a set of tx hashes (IDs), either loads them from local disk or asks the remote peer to provide them.
 *
 * A malicious response in which the data provided by the remote peer does not hash to the requested hash results in
 * [FetchDataFlow.DownloadedVsRequestedDataMismatch] being thrown.
 * If the remote peer doesn't have an entry, it results in a [FetchDataFlow.HashNotFound] exception.
 * If the remote peer is not authorized to request this transaction, it results in a [FetchDataFlow.IllegalTransactionRequest] exception.
 * Authorisation is accorded only on valid ancestors of the root transation.
 * Note that returned transactions are not inserted into the database, because it's up to the caller to actually verify the transactions are valid.
 */
class FetchTransactionsFlow(requests: Set<SecureHash>, otherSide: FlowSession) :
        FetchDataFlow<SignedTransaction, SignedTransaction>(requests, otherSide, DataType.TRANSACTION) {

    override fun load(txid: SecureHash): SignedTransaction? = serviceHub.validatedTransactions.getTransaction(txid)
}
