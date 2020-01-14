package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.NamedByHash
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.MaybeSerializedSignedTransaction
import net.corda.core.internal.FetchDataFlow.DownloadedVsRequestedDataMismatch
import net.corda.core.internal.FetchDataFlow.HashNotFound
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.debug
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

val DEFAULT_MAX_TRANSACTION_SIZE = 499 //++++ make 10MB once tested.

sealed class FetchDataFlow<T : NamedByHash, in W : Any>(
        protected val requests: Set<SecureHash>,
        protected val otherSideSession: FlowSession,
        protected val dataType: DataType,
        protected val maxTransactionSize: Int /*++++*/) : FlowLogic<FetchDataFlow.Result<T>>() {

/*++++    companion object {
        protected val DEFAULT_MAX_TRANSACTION_SIZE = 499999 //++++ make 10MB once tested.
    }*/

    @CordaSerializable
    class DownloadedVsRequestedDataMismatch(val requested: SecureHash, val got: SecureHash) : IllegalArgumentException()

    @CordaSerializable
    class DownloadedVsRequestedSizeMismatch(val requested: Int, val got: Int) : IllegalArgumentException()

    class HashNotFound(val requested: SecureHash) : FlowException()

    class MissingNetworkParameters(val requested: SecureHash) : FlowException("Failed to fetch network parameters with hash: $requested")

    class IllegalTransactionRequest(val requested: SecureHash) : FlowException("Illegal attempt to request a transaction (${requested}) that is not in the transitive dependency graph of the sent transaction.")

    @CordaSerializable
    data class Result<out T : NamedByHash>(val fromDisk: List<T>, val downloaded: List<T>)

    @CordaSerializable
    sealed class Request {
        data class Data(val hashes: NonEmptySet<SecureHash>, val dataType: DataType) : Request()
        object End : Request()
    }

    //++++ add annotation regarding default usage. Also add value for does not match.
    // https://docs.corda.net/serialization-enum-evolution.html
    // Below annotations added to map two new enum values (MULTI_TRANSACTION and UNKNOWN) onto  TRANSACTION. The effect of this is that
    // if a that does not have these enum values receives it will not throw an error during deserialization. The purpose of adding
    // UNKNOWN is such that future additions can default to UNKNOWN rather than an existing value. In this instance we are protecting
    // against not having unknown by using the platform version as a guard.
    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault("MULTI_TRANSACTION", "TRANSACTION"),
            CordaSerializationTransformEnumDefault("UNKNOWN", "TRANSACTION")
    )
    @CordaSerializable
    enum class DataType {
        TRANSACTION, ATTACHMENT, PARAMETERS, MULTI_TRANSACTION, UNKNOWN
    }

    @Suspendable
    @Throws(HashNotFound::class, MissingNetworkParameters::class)
    override fun call(): Result<T> {
        // Load the items we have from disk and figure out which we're missing.
        val (fromDisk, toFetch) = loadWhatWeHave()
        val fds = fromDisk.size //++++ remove
        val tofs = toFetch.size //++++ remove
        println("\nFetchDataFlow.call(): From disk size = $fds, to fetch size = $tofs, Max-Transactions Size = $maxTransactionSize")//++++ remove


        return if (toFetch.isEmpty()) {
            println("return early (empty).++++") //++++ trace message
            val loadedFromDisk = loadExpected(fromDisk)
            Result(loadedFromDisk, emptyList())
        } else {
            logger.debug { "Requesting ${toFetch.size} dependency(s) for verification from ${otherSideSession.counterparty.name}" }

            // TODO: Support "large message" response streaming so response sizes are not limited by RAM.
            // We can then switch to requesting items in large batches to minimise the latency penalty.
            // This is blocked by bugs ARTEMIS-1278 and ARTEMIS-1279. For now we limit attachments and txns to 10mb each
            // and don't request items in batch, which is a performance loss, but works around the issue. We have
            // configured Artemis to not fragment messages up to 10mb so we can send 10mb messages without problems.
            // Above that, we start losing authentication data on the message fragments and take exceptions in the
            // network layer.
            val maybeItems = ArrayList<W>()

//++++            val maybeItems = ArrayList<W>(toFetch.size)



           // val platformVersion = otherSideSession.getCounterpartyFlowInfo().flowVersion // For system flows this is platform version
           // println("PLAT VER OTHER SIDE = '${platformVersion}'")//++++ trace
            //++++ include platform version test ....
            if (toFetch.size == 1) {
                for (hash in toFetch) {
                    // Technically not necessary to loop when the outer test is for one item only. Safer than toFetch[0]
                    // We skip the validation here (with unwrap { it }) because we will do it below in validateFetchResponse.
                    // The only thing checked is the object type. It is a protocol violation to send results out of order.
                    // TODO We need to page here after large messages will work.
                    println("otherSideSession.sendAndReceive($hash)")//++++
                    // should only pass single item dataType below.
                    maybeItems += otherSideSession.sendAndReceive<List<W>>(Request.Data(NonEmptySet.of(hash), dataType)).unwrap { it }
                    println("FetchDataFlow.call3b ++++ $hash : ${dataType.name}")
                }
            } else { //++++ XXXX
                // val tofs = toFetch.size
                // println("FOR START ++++($tofs)")
                //++++var index: Int = 0

                val fetchSet = LinkedHashSet<SecureHash>()
                for (hash in toFetch) {
                    fetchSet.add(hash)
                }
                println("END FOR ++++(${fetchSet.size})")

                //val fetchItems = NonEmptySet<SecureHash>.copyOf(toFetch)
                println("otherSideSession.sendAndReceive(list of ${fetchSet.size})")//++++
                maybeItems += otherSideSession.sendAndReceive<List<W>>(Request.Data(NonEmptySet.copyOf(fetchSet), dataType))
                        .unwrap { it }
                println("FetchDataFlow.call3b ++++ : ${dataType.name}")

                //++++    maybeItems += otherSideSession.sendAndReceive<List<W>>(Request.Data(NonEmptySet.of(toFetch.get(0), toFetch.get(1)), dataType))
                //++++ .unwrap { it } //++++ nasty POC hack use 2 items
                val mb1 = maybeItems.size
                println("FetchDataFlow.otherSideSession.sendAndReceive done ($mb1) ++++ ${dataType.name}")
            }




/*OLD - remove +++++
            for (hash in toFetch) {
                // We skip the validation here (with unwrap { it }) because we will do it below in validateFetchResponse.
                // The only thing checked is the object type. It is a protocol violation to send results out of order.
                // TODO We need to page here after large messages will work.
                maybeItems += otherSideSession.sendAndReceive<List<W>>(Request.Data(NonEmptySet.of(hash), dataType)).unwrap { it }
            }

 ++++*/

            //++++ remove copy // Check for a buggy/malicious peer answering with something that we didn't ask for.
//++++ remove old            val downloaded = validateFetchResponse(UntrustworthyData(maybeItems), toFetch)

            // Check for a buggy/malicious peer answering with something that we didn't ask for.
            val mb2 = maybeItems.size//++++
            println("validating fetch response ++++maybe2_sz=$mb2, tofetch_sz=$tofs")
            val downloaded = validateFetchResponse(UntrustworthyData(maybeItems), toFetch)
            println("downloaded fetch response ++++(downloaded_sz=${downloaded.size}) maybe2=$mb2, cls=${downloaded.javaClass.name}")




            logger.debug { "Fetched ${downloaded.size} elements from ${otherSideSession.counterparty.name}" }
            maybeWriteToDisk(downloaded)
            // Re-load items already present before the download procedure. This ensures these objects are not unnecessarily checkpointed.
            val loadedFromDisk = loadExpected(fromDisk)


            for (dnd in loadedFromDisk) {
                println("DN-DISK = '${dnd.javaClass.name} '${dnd.id}'")//++++
            }
            for (dnl in downloaded) {
                println("DN-REMO = '${dnl.javaClass.name} '${dnl.id}'")//++++
            }



            Result(loadedFromDisk, downloaded)
        }
    }

    protected open fun maybeWriteToDisk(downloaded: List<T>) {
        // Do nothing by default.
    }

    private fun loadWhatWeHave(): Pair<List<SecureHash>, List<SecureHash>> {
        val fromDisk = ArrayList<SecureHash>()
        val toFetch = ArrayList<SecureHash>()
        for (txid in requests) {
            val stx = load(txid)
            if (stx == null)
                toFetch += txid
            else
                // Although the full object is loaded here, only return the id. This prevents the full set of objects already present from
                // being checkpointed every time a request is made to download an object the node does not yet have.
                fromDisk += txid
        }
        return Pair(fromDisk, toFetch)
    }

    private fun loadExpected(ids: List<SecureHash>): List<T> {
        val loaded = ids.mapNotNull { load(it) }
        require(ids.size == loaded.size) {
            "Expected to find ${ids.size} items in database but only found ${loaded.size} items"
        }
        return loaded
    }

    protected abstract fun load(txid: SecureHash): T?

    protected open fun convert(wire: W): T = uncheckedCast(wire)

    private fun validateFetchResponse(maybeItems: UntrustworthyData<ArrayList<W>>,
                                      requests: List<SecureHash>): List<T> {
        return maybeItems.unwrap { response ->
            println("PT U0++++ ${response.size} ${requests.size}")//++++
            if (response.size != requests.size) {
                println("PT U1++++ response.size != requests.size")
                throw DownloadedVsRequestedSizeMismatch(requests.size, response.size)
            }
            println("U0a: type=${response.javaClass.name} ++++")
            for (rr in response) { //++++
                println("  RespType = ${rr.javaClass.name}") // ++++ make trace level
            }

            val answers = response.map { convert(it) }
            println("PT U2++++ ans=${answers.javaClass.name} ${answers.size}")//++++
            for (aa in answers) {
                if (aa is SignedTransaction) {
                    println("PT U2a++++ ${aa.javaClass.name}: SignedTran: tx size = ${aa.txBits.size}")
                } else if (aa is MaybeSerializedSignedTransaction) {
                    val cnt = if (aa.isNull()) { "<Null>" } else if (aa.serialized != null) { aa.serialized.size } else { -1 }
                    println("PT U2a++++ ${aa.javaClass.name}: MayBe: tx size = $cnt")
                } else {
                    println("PT U2a++++ ${aa.javaClass.name}: Unknown")
                }
            }
            for (req in requests) {
                println("REQ = '${req}")//++++
            }

            println("PT U3++++ ${answers.size}")
            // Check transactions actually hash to what we requested, if this fails the remote node
            // is a malicious flow violator or buggy.
            var badDataIndex = -1
            var badDataId : SecureHash? = null //++++SecureHash("NOT-SET"
            for ((index, item) in answers.withIndex()) {
                println("PT U5++++ Index='${requests[index]}' item='${item.id}'")

                if (item.id != requests[index]) {
                    println("PT U5t++++ ${item.id} ${requests[index]}")
                    badDataIndex = index //++++
                    badDataId = item.id
                    println("Will Throw: DownloadedVsRequestedDataMismatch(${requests[index]}, ${item.id}")
                }
            }
            if (badDataIndex >= 0 && badDataId != null) {
                throw DownloadedVsRequestedDataMismatch(requests[badDataIndex], badDataId)
            }

            answers
        }
/* ++++ remove old
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

 ++++*/
    }
}

/**
 * Given a set of hashes either loads from local storage or requests them from the other peer. Downloaded
 * attachments are saved to local storage automatically.
 */
class FetchAttachmentsFlow(requests: Set<SecureHash>,
                           otherSide: FlowSession) : FetchDataFlow<Attachment, ByteArray>(requests, otherSide, DataType.ATTACHMENT, DEFAULT_MAX_TRANSACTION_SIZE) {

    private val uploader = "$P2P_UPLOADER:${otherSideSession.counterparty.name}"

    override fun load(txid: SecureHash): Attachment? = serviceHub.attachments.openAttachment(txid)

    override fun convert(wire: ByteArray): Attachment = FetchedAttachment({ wire }, uploader)

    override fun maybeWriteToDisk(downloaded: List<Attachment>) {
        for (attachment in downloaded) {
            with(serviceHub.attachments) {
                if (!hasAttachment(attachment.id)) {
                    try {
                        importAttachment(attachment.open(), uploader, null)
                    } catch (e: FileAlreadyExistsException) {
                        // This can happen when another transaction will insert the same attachment during this transaction.
                        // The outcome is the same (the attachment is imported), so we can ignore this exception.
                        logger.debug { "Attachment ${attachment.id} already inserted." }
                    }
                } else {
                    logger.debug { "Attachment ${attachment.id} already exists, skipping." }
                }
            }
        }
    }

    private class FetchedAttachment(dataLoader: () -> ByteArray, uploader: String?) : AbstractAttachment(dataLoader, uploader), SerializeAsToken {
        override val id: SecureHash by lazy { attachmentData.sha256() }

        private class Token(private val id: SecureHash, private val uploader: String?) : SerializationToken {
            override fun fromToken(context: SerializeAsTokenContext) = FetchedAttachment(context.attachmentDataLoader(id), uploader)
        }

        override fun toToken(context: SerializeAsTokenContext) = Token(id, uploader)
    }
}

/**
 * Given a set of tx hashes (IDs), either loads them from local disk or asks the remote peer to provide them.
 *
 * A malicious response in which the data provided by the remote peer does not hash to the requested hash results in
 * [FetchDataFlow.DownloadedVsRequestedDataMismatch] being thrown.
 * If the remote peer doesn't have an entry, it results in a [FetchDataFlow.HashNotFound] exception.
 * If the remote peer is not authorized to request this transaction, it results in a [FetchDataFlow.IllegalTransactionRequest] exception.
 * Authorisation is accorded only on valid ancestors of the root transaction.
 * Note that returned transactions are not inserted into the database, because it's up to the caller to actually verify the transactions are valid.
 */
class FetchTransactionsFlow(requests: Set<SecureHash>, otherSide: FlowSession) :
        FetchDataFlow<SignedTransaction, SignedTransaction>(requests, otherSide, DataType.TRANSACTION, DEFAULT_MAX_TRANSACTION_SIZE) {

    override fun load(txid: SecureHash): SignedTransaction? = serviceHub.validatedTransactions.getTransaction(txid)
}

//++++ added start: Move to enterprise?
class FetchMultiTransactionsFlow(requests: Set<SecureHash>, otherSide: FlowSession, maxTransactionSize: Int) : //++++
        FetchDataFlow<MaybeSerializedSignedTransaction, MaybeSerializedSignedTransaction>(requests, otherSide, DataType.MULTI_TRANSACTION, maxTransactionSize) { //++++

    //override fun namexxxx() : String { return "FetchMultiTransactionsFlow"} //++++
    //override fun load(txid: SecureHash): SignedTransaction? = serviceHub.validatedTransactions.getTransaction(txid)
    override fun load(txid: SecureHash): MaybeSerializedSignedTransaction? {
        val tran = serviceHub.validatedTransactions.getTransaction(txid)
        return if (tran == null) {
            null
        } else {
            MaybeSerializedSignedTransaction(txid, null, tran)
        }
    }
}
//++++ added end

/**
 * Given a set of hashes either loads from local network parameters storage or requests them from the other peer. Downloaded
 * network parameters are saved to local parameters storage automatically. This flow can be used only if the minimumPlatformVersion is >= 4.
 * Nodes on lower versions won't respond to this flow.
 */
class FetchNetworkParametersFlow(requests: Set<SecureHash>,
                                 otherSide: FlowSession) : FetchDataFlow<SignedDataWithCert<NetworkParameters>,
                                 SignedDataWithCert<NetworkParameters>>(requests, otherSide, DataType.PARAMETERS,
                                 DEFAULT_MAX_TRANSACTION_SIZE) {
    override fun load(txid: SecureHash): SignedDataWithCert<NetworkParameters>? {
        return (serviceHub.networkParametersService as NetworkParametersStorage).lookupSigned(txid)
    }

    override fun maybeWriteToDisk(downloaded: List<SignedDataWithCert<NetworkParameters>>) {
        for (parameters in downloaded) {
            with(serviceHub.networkParametersService as NetworkParametersStorage) {
                if (!hasParameters(parameters.id)) {
                    // This will perform the signature check too and throws SignatureVerificationException
                    saveParameters(parameters)
                } else {
                    logger.debug { "Network parameters ${parameters.id} already exists in storage, skipping." }
                }
            }
        }
    }
}