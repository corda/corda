package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.SlidingWindowReservoir
import com.google.common.base.Stopwatch
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.Party
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.elapsedTime
import net.corda.core.internal.notary.*
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*
import kotlin.concurrent.thread

/** A RDBMS backed Uniqueness provider */
@ThreadSafe
class PersistentUniquenessProvider(
        metrics: MetricRegistry,
        val clock: Clock,
        val database: CordaPersistence,
        cacheFactory: NamedCacheFactory
) : UniquenessProvider, SingletonSerializeAsToken() {

    @MappedSuperclass
    class BaseComittedState(
            @EmbeddedId
            val id: PersistentStateRef,

            @Column(name = "consuming_transaction_id", nullable = true)
            val consumingTxHash: String?
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_request_log")
    @CordaSerializable
    class Request(
            @Id
            @GeneratedValue
            @Column(nullable = true)
            val id: Int? = null,

            @Column(name = "consuming_transaction_id", nullable = true)
            val consumingTxHash: String?,

            @Column(name = "requesting_party_name", nullable = true)
            var partyName: String?,

            @Lob
            @Column(name = "request_signature", nullable = false)
            val requestSignature: ByteArray,

            @Column(name = "request_timestamp", nullable = false)
            var requestDate: Instant
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_committed_txs")
    class CommittedTransaction(
            @Id
            @Column(name = "transaction_id", nullable = false, length = 64)
            val transactionId: String
    )

    private data class CommitRequest(
            val states: List<StateRef>,
            val txId: SecureHash,
            val callerIdentity: Party,
            val requestSignature: NotarisationRequestSignature,
            val timeWindow: TimeWindow?,
            val references: List<StateRef>,
            val future: OpenFuture<UniquenessProvider.Result>)

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_committed_states")
    class CommittedState(id: PersistentStateRef, consumingTxHash: String) : BaseComittedState(id, consumingTxHash)

    private val metricPrefix = PersistentUniquenessProvider::class.simpleName
    /** Transaction commit duration timer and TPS meter. */
    private val commitTimer = metrics.timer("$metricPrefix.Commit")
    /** IPS (input states per second) meter. */
    private val inputStatesMeter = metrics.meter("$metricPrefix.IPS")
    /** Track double spend attempts. Note that this will also include notarisation retries. */
    private val conflictCounter = metrics.counter("$metricPrefix.Conflicts")
    /** Track the distribution of the number of input states. **/
    private val inputStateHistogram = metrics.histogram("$metricPrefix.NumberOfInputStates")
    /** Track the measured ETA. **/
    private val requestProcessingETA = metrics.histogram("$metricPrefix.requestProcessingETASeconds")
    /** Track the number of requests in the queue at insert. **/
    private val requestQueueCount = metrics.histogram("$metricPrefix.requestQueue.size")
    /** Track the number of states in the queue at insert. **/
    private val requestQueueStateCount = metrics.histogram("$metricPrefix.requestQueue.queuedStates")
    /** Tracks the distribution of the number of unique transactions that contributed states to the current transaction **/
    private val uniqueTxHashCount = metrics.histogram("$metricPrefix.NumberOfUniqueTxHashes")
    private val commitLog = createMap(cacheFactory)

    private val requestQueue = LinkedBlockingQueue<CommitRequest>(requestQueueSize)
    private val nrQueuedStates = AtomicInteger(0)

    /**
     * Measured in states per minute, with a minimum of 1. We take an average of the last 100 commits.
     * Minutes was chosen to increase accuracy by 60x over seconds, given we have to use longs here.
     */
    private val throughputHistory = SlidingWindowReservoir(100)
    @Volatile
    private var throughput: Double = 0.0

    /**
     * Estimated time of request processing.
     * This uses performance metrics to gauge how long the wait time for a newly queued state will probably be.
     * It checks that there is actual traffic going on (i.e. a non-zero number of states are queued and there
     * is actual throughput) and then returns the expected wait time scaled up by a factor of 2 to give a probable
     * upper bound.
     *
     * @param numStates The number of states (input + reference) we're about to request be notarised.
     */
    override fun getEta(numStates: Int): Duration {
        val rate = throughput
        val nrStates = nrQueuedStates.getAndAdd(numStates)
        requestQueueStateCount.update(nrStates)
        log.debug { "rate: $rate, queueSize: $nrStates" }
        if (rate > 0.0 && nrStates > 0) {
            val eta = Duration.ofSeconds((2 * TimeUnit.MINUTES.toSeconds(1) * nrStates / rate).toLong())
            requestProcessingETA.update(eta.seconds)
            return eta
        }
        return NotaryServiceFlow.defaultEstimatedWaitTime
    }

    /** A request processor thread. */
    private val processorThread = thread(name = "Notary request queue processor", isDaemon = true) {
        try {
            while (!Thread.interrupted()) {
                processRequest(requestQueue.take())
            }
        } catch (e: InterruptedException) {
        }
        log.debug { "Shutting down with ${requestQueue.size} in-flight requests unprocessed." }
    }

    companion object {
        private const val requestQueueSize = 100_000
        private val log = contextLogger()
        fun createMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<StateRef, SecureHash, CommittedState, PersistentStateRef> =
                AppendOnlyPersistentMap(
                        cacheFactory = cacheFactory,
                        name = "PersistentUniquenessProvider_transactions",
                        toPersistentEntityKey = { PersistentStateRef(it.txhash.toString(), it.index) },
                        fromPersistentEntity = {
                            //TODO null check will become obsolete after making DB/JPA columns not nullable
                            val txId = it.id.txId
                            val index = it.id.index
                            Pair(
                                    StateRef(txhash = SecureHash.parse(txId), index = index),
                                    SecureHash.parse(it.consumingTxHash)
                            )

                        },
                        toPersistentEntity = { (txHash, index): StateRef, id: SecureHash ->
                            CommittedState(
                                    id = PersistentStateRef(txHash.toString(), index),
                                    consumingTxHash = id.toString()
                            )
                        },
                        persistentEntityClass = CommittedState::class.java
                )
    }

    /**
     * Generates and adds a [CommitRequest] to the request queue. If the request queue is full, this method will block
     * until space is available.
     *
     * Returns a future that will complete once the request is processed, containing the commit [Result].
     */
    override fun commit(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>
    ): CordaFuture<UniquenessProvider.Result> {
        inputStateHistogram.update(states.size)
        val timer = Stopwatch.createStarted()
        val future = openFuture<UniquenessProvider.Result>()
        val request = CommitRequest(states, txId, callerIdentity, requestSignature, timeWindow, references, future)
        future.then {
            recordDuration(timer)
        }
        requestQueue.put(request)
        requestQueueCount.update(requestQueue.size)
        return future
    }

    private fun recordDuration(totalTime: Stopwatch) {
        totalTime.stop()
        val elapsed = totalTime.elapsed(TimeUnit.MILLISECONDS)
        commitTimer.update(elapsed, TimeUnit.MILLISECONDS)
    }

    private fun logRequest(txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature) {
        val request = Request(
                consumingTxHash = txId.toString(),
                partyName = callerIdentity.name.toString(),
                requestSignature = requestSignature.serialize().bytes,
                requestDate = clock.instant()
        )
        val session = currentDBSession()
        session.persist(request)
    }

    private fun findAlreadyCommitted(
            states: List<StateRef>,
            references: List<StateRef>,
            commitLog: AppendOnlyPersistentMap<StateRef, SecureHash, CommittedState, PersistentStateRef>
    ): LinkedHashMap<StateRef, StateConsumptionDetails> {
        val conflictingStates = LinkedHashMap<StateRef, StateConsumptionDetails>()

        fun checkConflicts(toCheck: List<StateRef>, type: StateConsumptionDetails.ConsumedStateType) {
            return toCheck.forEach { stateRef ->
                val consumingTx = commitLog[stateRef]
                if (consumingTx != null) conflictingStates[stateRef] = StateConsumptionDetails(consumingTx.sha256(), type)
            }
        }

        checkConflicts(states, StateConsumptionDetails.ConsumedStateType.INPUT_STATE)
        checkConflicts(references, StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE)

        return conflictingStates
    }

    private fun commitOne(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>
    ) {
        database.transaction {
            logRequest(txId, callerIdentity, requestSignature)
            val conflictingStates = findAlreadyCommitted(states, references, commitLog)
            if (conflictingStates.isNotEmpty()) {
                if (states.isEmpty()) {
                    handleReferenceConflicts(txId, conflictingStates)
                } else {
                    handleConflicts(txId, conflictingStates)
                }
            } else {
                handleNoConflicts(timeWindow, states, txId, commitLog)
            }
        }
    }

    private fun previouslyCommitted(txId: SecureHash): Boolean {
        val session = currentDBSession()
        return session.find(CommittedTransaction::class.java, txId.toString()) != null
    }

    private fun handleReferenceConflicts(txId: SecureHash, conflictingStates: LinkedHashMap<StateRef, StateConsumptionDetails>) {
        if (!previouslyCommitted(txId)) {
            conflictCounter.inc()
            val conflictError = NotaryError.Conflict(txId, conflictingStates)
            log.debug { "Failure, input states already committed: ${conflictingStates.keys}" }
            throw NotaryInternalException(conflictError)
        }
        log.debug { "Transaction $txId already notarised" }
    }

    private fun handleConflicts(txId: SecureHash, conflictingStates: LinkedHashMap<StateRef, StateConsumptionDetails>) {
        if (isConsumedByTheSameTx(txId.sha256(), conflictingStates)) {
            log.debug { "Transaction $txId already notarised" }
            return
        } else {
            log.debug { "Failure, input states already committed: ${conflictingStates.keys}" }
            conflictCounter.inc()
            val conflictError = NotaryError.Conflict(txId, conflictingStates)
            throw NotaryInternalException(conflictError)
        }
    }

    private fun handleNoConflicts(timeWindow: TimeWindow?, states: List<StateRef>, txId: SecureHash, commitLog: AppendOnlyPersistentMap<StateRef, SecureHash, CommittedState, PersistentStateRef>) {
        // Skip if this is a re-notarisation of a reference-only transaction
        if (states.isEmpty() && previouslyCommitted(txId)) {
            return
        }

        val outsideTimeWindowError = validateTimeWindow(clock.instant(), timeWindow)
        if (outsideTimeWindowError == null) {
            states.forEach { stateRef ->
                commitLog[stateRef] = txId
            }
            val session = currentDBSession()
            session.persist(CommittedTransaction(txId.toString()))
            log.debug { "Successfully committed all input states: $states" }
        } else {
            throw NotaryInternalException(outsideTimeWindowError)
        }
    }

    private fun decrementQueueSize(request: CommitRequest): Int {
        val nrStates = request.states.size + request.references.size
        nrQueuedStates.addAndGet(-nrStates)
        return nrStates
    }

    private fun processRequest(request: CommitRequest) {
        val numStates = decrementQueueSize(request)
        try {
            uniqueTxHashCount.update(request.states.distinctBy { it.txhash }.count())
            val duration = elapsedTime {
                commitOne(request.states, request.txId, request.callerIdentity, request.requestSignature, request.timeWindow, request.references)
            }
            inputStatesMeter.mark(request.states.size.toLong())
            val statesPerMinute = numStates.toLong() * TimeUnit.MINUTES.toNanos(1) / duration.toNanos()
            throughputHistory.update(maxOf(statesPerMinute, 1))
            throughput = throughputHistory.snapshot.median // Median deemed more stable / representative than mean.
            respondWithSuccess(request)
        } catch (e: Exception) {
            log.warn("Error processing commit request", e)
            respondWithError(request, e)
        }
    }

    private fun respondWithError(request: CommitRequest, exception: Exception) {
        if (exception is NotaryInternalException) {
            request.future.set(UniquenessProvider.Result.Failure(exception.error))
        } else {
            request.future.setException(NotaryInternalException(NotaryError.General(Exception("Internal service error."))))
        }
    }

    private fun respondWithSuccess(request: CommitRequest) {
        request.future.set(UniquenessProvider.Result.Success)
    }
}
