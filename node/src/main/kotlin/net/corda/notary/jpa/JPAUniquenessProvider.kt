package net.corda.notary.jpa

import com.google.common.collect.Queues
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.notary.common.BatchSigningFunction
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.internal.notary.isConsumedByTheSameTx
import net.corda.core.internal.notary.validateTimeWindow
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.notary.common.InternalResult
import net.corda.serialization.internal.CordaSerializationEncoding
import org.hibernate.Session
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.NamedQuery
import kotlin.concurrent.thread

/** A JPA backed Uniqueness provider */
@Suppress("MagicNumber") // database column length
@ThreadSafe
class JPAUniquenessProvider(
        val clock: Clock,
        val database: CordaPersistence,
        val config: JPANotaryConfiguration = JPANotaryConfiguration(),
        val notaryWorkerName: CordaX500Name,
        val signBatch: BatchSigningFunction
) : UniquenessProvider, SingletonSerializeAsToken() {

    // This is the prefix of the ID in the request log table, to allow running multiple instances that access the
    // same table.
    private val instanceId = UUID.randomUUID()

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_request_log")
    @CordaSerializable
    class Request(
            @Id
            @Column(nullable = true, length = 76)
            var id: String? = null,

            @Column(name = "consuming_transaction_id", nullable = true, length = 64)
            val consumingTxHash: String?,

            @Column(name = "requesting_party_name", nullable = true, length = 255)
            var partyName: String?,

            @Lob
            @Column(name = "request_signature", nullable = false)
            val requestSignature: ByteArray,

            @Column(name = "request_timestamp", nullable = false)
            var requestDate: Instant,

            @Column(name = "worker_node_x500_name", nullable = true, length = 255)
            val workerNodeX500Name: String?
    )

    private data class CommitRequest(
            val states: List<StateRef>,
            val txId: SecureHash,
            val callerIdentity: Party,
            val requestSignature: NotarisationRequestSignature,
            val timeWindow: TimeWindow?,
            val references: List<StateRef>,
            val future: OpenFuture<UniquenessProvider.Result>,
            val requestEntity: Request,
            val committedStatesEntities: List<CommittedState>)

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_committed_states")
    @NamedQuery(name = "CommittedState.select", query = "SELECT c from JPAUniquenessProvider\$CommittedState c WHERE c.id in :ids")
    class CommittedState(
            @EmbeddedId
            val id: PersistentStateRef,
            @Column(name = "consuming_transaction_id", nullable = false, length = 64)
            val consumingTxHash: String)

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_committed_txs")
    class CommittedTransaction(
            @Id
            @Column(name = "transaction_id", nullable = false, length = 64)
            val transactionId: String
    )

    private val requestQueue = LinkedBlockingQueue<CommitRequest>(requestQueueSize)

    /** A requestEntity processor thread. */
    private val processorThread = thread(name = "Notary request queue processor", isDaemon = true) {
        try {
            val buffer = LinkedList<CommitRequest>()
            while (!Thread.interrupted()) {
                val drainedSize = Queues.drain(requestQueue, buffer, config.batchSize, config.batchTimeoutMs, TimeUnit.MILLISECONDS)
                if (drainedSize == 0) continue
                processRequests(buffer)
                buffer.clear()
            }
        } catch (_: InterruptedException) {
            log.debug { "Process interrupted."}
        }
        log.debug { "Shutting down with ${requestQueue.size} in-flight requests unprocessed." }
    }

    fun stop() {
        processorThread.interrupt()
    }

    companion object {
        private const val requestQueueSize = 100_000
        private const val jdbcBatchSize = 100_000
        private val log = contextLogger()

        fun encodeStateRef(s: StateRef): PersistentStateRef {
            return PersistentStateRef(s.txhash.toString(), s.index)
        }

        fun decodeStateRef(s: PersistentStateRef): StateRef {
            return StateRef(txhash = SecureHash.parse(s.txId), index = s.index)
        }
    }

    /**
     * Generates and adds a [CommitRequest] to the requestEntity queue. If the requestEntity queue is full, this method will block
     * until space is available.
     *
     * Returns a future that will complete once the requestEntity is processed, containing the commit [Result].
     */
    override fun commit(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>,
            notary: Party? /** TODO: implement for batch signing  */
    ): CordaFuture<UniquenessProvider.Result> {
        val future = openFuture<UniquenessProvider.Result>()
        val requestEntities = Request(consumingTxHash = txId.toString(),
                partyName = callerIdentity.name.toString(),
                requestSignature = requestSignature.serialize(context = SerializationDefaults.STORAGE_CONTEXT.withEncoding(CordaSerializationEncoding.SNAPPY)).bytes,
                requestDate = clock.instant(),
                workerNodeX500Name = notaryWorkerName.toString())
        val stateEntities = states.map {
            CommittedState(
                    encodeStateRef(it),
                    txId.toString()
            )
        }
        val request = CommitRequest(states, txId, callerIdentity, requestSignature, timeWindow, references, future, requestEntities, stateEntities)

        requestQueue.put(request)

        return future
    }

    // Safe up to 100k requests per second.
    private var nextRequestId = System.currentTimeMillis() * 100

    private fun logRequests(requests: List<CommitRequest>) {
        database.transaction {
            for (request in requests) {
                request.requestEntity.id = "$instanceId:${(nextRequestId++).toString(16)}"
                session.persist(request.requestEntity)
            }
        }
    }

    private fun commitRequests(session: Session, requests: List<CommitRequest>) {
        for (request in requests) {
            for (cs in request.committedStatesEntities) {
                session.persist(cs)
            }
            session.persist(CommittedTransaction(request.txId.toString()))
        }
    }

    private fun findAlreadyCommitted(session: Session, states: List<StateRef>, references: List<StateRef>): Map<StateRef, StateConsumptionDetails> {
        val persistentStateRefs = (states + references).map { encodeStateRef(it) }.toSet()
        val committedStates = mutableListOf<CommittedState>()

        for (idsBatch in persistentStateRefs.chunked(config.maxInputStates)) {
            @Suppress("UNCHECKED_CAST")
            val existing = session
                    .createNamedQuery("CommittedState.select")
                    .setParameter("ids", idsBatch)
                    .resultList as List<CommittedState>
            committedStates.addAll(existing)
        }

        return committedStates.map {
            val stateRef = StateRef(txhash = SecureHash.parse(it.id.txId), index = it.id.index)
            val consumingTxId = SecureHash.parse(it.consumingTxHash)
            if (stateRef in references) {
                stateRef to StateConsumptionDetails(consumingTxId.sha256(), type = StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE)
            } else {
                stateRef to StateConsumptionDetails(consumingTxId.sha256())
            }
        }.toMap()
    }

    private fun<T> withRetry(block: () -> T): T {
        var retryCount = 0
        var backOff = config.backOffBaseMs
        var exceptionCaught: SQLException? = null
        while (retryCount <= config.maxDBTransactionRetryCount) {
            try {
                val res = block()
                return res
            } catch (e: SQLException) {
                retryCount++
                Thread.sleep(backOff)
                backOff *= 2
                exceptionCaught = e
            }
        }
        throw exceptionCaught!!
    }

    private fun findAllConflicts(session: Session, requests: List<CommitRequest>): MutableMap<StateRef, StateConsumptionDetails> {
        log.info("Processing notarization requests with ${requests.sumBy { it.states.size }} input states and ${requests.sumBy { it.references.size }} references")

        val allStates = requests.flatMap { it.states }
        val allReferences = requests.flatMap { it.references }
        return findAlreadyCommitted(session, allStates, allReferences).toMutableMap()
    }

    private fun processRequest(
            session: Session,
            request: CommitRequest,
            consumedStates: MutableMap<StateRef, StateConsumptionDetails>,
            processedTxIds: MutableMap<SecureHash, InternalResult>,
            toCommit: MutableList<CommitRequest>
    ): InternalResult {
        val conflicts = (request.states + request.references).mapNotNull {
            if (consumedStates.containsKey(it)) it to consumedStates[it]!!
            else null
        }.toMap()

        return if (conflicts.isNotEmpty()) {
            handleStateConflicts(request, conflicts, session)
        } else {
            handleNoStateConflicts(request, toCommit, consumedStates, processedTxIds, session)
        }
    }

    /**
     * Process the [request] given there are conflicting states already present in the DB or current batch.
     *
     * To ensure idempotency, if the request's transaction matches a previously consumed transaction then the
     * same result (success) can be returned without committing it to the DB. Failure is only returned in the
     * case where the request is not a duplicate of a previously processed request and hence it is a genuine
     * double spend attempt.
     */
    private fun handleStateConflicts(
            request: CommitRequest,
            stateConflicts: Map<StateRef, StateConsumptionDetails>,
            session: Session
    ): InternalResult {
        return when {
            isConsumedByTheSameTx(request.txId.sha256(), stateConflicts) -> {
                InternalResult.Success
            }
            request.states.isEmpty() && isPreviouslyNotarised(session, request.txId) -> {
                InternalResult.Success
            }
            else -> {
                InternalResult.Failure(NotaryError.Conflict(request.txId, stateConflicts))
            }
        }
    }

    /**
     * Process the [request] given there are no conflicting states already present in the DB or current batch.
     *
     * This method performs time window validation and adds the request to the commit list if applicable.
     * It also checks the [processedTxIds] map to ensure that any time-window only duplicates within the batch
     * are only committed once.
     */
    private fun handleNoStateConflicts(
            request: CommitRequest,
            toCommit: MutableList<CommitRequest>,
            consumedStates: MutableMap<StateRef, StateConsumptionDetails>,
            processedTxIds: MutableMap<SecureHash, InternalResult>,
            session: Session
    ): InternalResult {
        return when {
            request.states.isEmpty() && isPreviouslyNotarised(session, request.txId) -> {
                InternalResult.Success
            }
            processedTxIds.containsKey(request.txId) -> {
                processedTxIds[request.txId]!!
            }
            else -> {
                val outsideTimeWindowError = validateTimeWindow(clock.instant(), request.timeWindow)
                val internalResult = if (outsideTimeWindowError != null) {
                    InternalResult.Failure(outsideTimeWindowError)
                } else {
                    // Mark states as consumed to capture conflicting transactions in the same batch
                    request.states.forEach {
                        consumedStates[it] = StateConsumptionDetails(request.txId.sha256())
                    }
                    toCommit.add(request)
                    InternalResult.Success
                }
                // Store transaction result to capture conflicting time-window only transactions in the same batch
                processedTxIds[request.txId] = internalResult
                internalResult
            }
        }
    }

    private fun isPreviouslyNotarised(session: Session, txId: SecureHash): Boolean {
        return session.find(CommittedTransaction::class.java, txId.toString()) != null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRequests(requests: List<CommitRequest>) {
        try {
            // Note that there is an additional retry mechanism within the transaction itself.
            val res = withRetry {
                database.transaction {
                    val em = session.entityManagerFactory.createEntityManager()
                    em.unwrap(Session::class.java).jdbcBatchSize = jdbcBatchSize

                    val toCommit = mutableListOf<CommitRequest>()
                    val consumedStates = findAllConflicts(session, requests)
                    val processedTxIds = mutableMapOf<SecureHash, InternalResult>()

                    val results = requests.map { request ->
                        processRequest(session, request, consumedStates, processedTxIds, toCommit)
                    }

                    logRequests(requests)
                    commitRequests(session, toCommit)

                    results
                }
            }
            completeResponses(requests, res)
        } catch (e: Exception) {
            log.warn("Error processing commit requests", e)
            for (request in requests) {
                respondWithError(request, e)
            }
        }
    }

    private fun completeResponses(requests: List<CommitRequest>, results: List<InternalResult>): Int {
        val zippedResults = requests.zip(results)
        val successfulRequests = zippedResults
                .filter { it.second is InternalResult.Success }
                .map { it.first.txId }
                .distinct()
        val signature = if (successfulRequests.isNotEmpty())
            signBatch(successfulRequests)
        else null

        var inputStateCount = 0
        for ((request, result) in zippedResults) {
            val resultToSet = when {
                result is InternalResult.Failure -> UniquenessProvider.Result.Failure(result.error)
                signature != null -> UniquenessProvider.Result.Success(signature.forParticipant(request.txId))
                else -> throw IllegalStateException("Signature is required but not found")
            }

            request.future.set(resultToSet)
            inputStateCount += request.states.size
        }
        return inputStateCount
    }

    private fun respondWithError(request: CommitRequest, exception: Exception) {
        if (exception is NotaryInternalException) {
            request.future.set(UniquenessProvider.Result.Failure(exception.error))
        } else {
            request.future.setException(NotaryInternalException(NotaryError.General(Exception("Internal service error."))))
        }
    }
}
