package net.corda.notary.standalonejpa

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.SlidingWindowReservoir
import com.google.common.base.Stopwatch
import com.google.common.collect.Queues
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.elapsedTime
import net.corda.core.internal.notary.*
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.serialization.internal.CordaSerializationEncoding
import org.hibernate.Session
import java.sql.BatchUpdateException
import java.sql.SQLNonTransientConnectionException
import java.sql.SQLRecoverableException
import java.sql.SQLTransactionRollbackException
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.sql.DataSource
import kotlin.concurrent.thread

/**
 * A JPA based uniqueness provider that does not depend on Corda persistence.
 * Will work with any database for which a JDBC driver can be provided. Note that the required data source
 * properties will vary with the database required.
 */
@ThreadSafe
class StandaloneJPAUniquenessProvider(
        val metrics: MetricRegistry,
        val clock: Clock,
        val config: StandaloneJPANotaryConfig
) : UniquenessProvider, SingletonSerializeAsToken() {

    /**
     * This is the prefix of the ID in the request log table, to allow running multiple instances that access the
     * same table.
     */
    val instanceId = UUID.randomUUID().toString()

    /**
     * Measured in states per minute, with a minimum of 1. We take an average of the last 100 commits.
     * Minutes was chosen to increase accuracy by 60x over seconds, given we have to use longs here.
     */
    private val throughputHistory = SlidingWindowReservoir(100)
    @Volatile
    private var throughput: Double = 0.0

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

    private val requestQueue = LinkedBlockingQueue<CommitRequest>(requestQueueSize)
    private val nrQueuedStates = AtomicInteger(0)

    private val metricPrefix = StandaloneJPAUniquenessProvider::class.simpleName
    /** Transaction commit duration timer and TPS meter. */
    private val commitTimer = metrics.timer("$metricPrefix.Commit")
    /** IPS (input states per second) meter. */
    private val inputStatesMeter = metrics.meter("$metricPrefix.IPS")
    /** Transaction batch commit duration and rate meter. */
    private val batchTimer = metrics.timer("$metricPrefix.BatchCommit")
    /**
     * When writing to multiple masters with Galera, transaction rollbacks may happen due to high write contention.
     * This is a useful heath metric.
     */
    private val rollbackCounter = metrics.counter("$metricPrefix.Rollback")
    /** Track double spend attempts. Note that this will also include notarisation retries. */
    private val conflictCounter = metrics.counter("$metricPrefix.Conflicts")
    /** Track the distribution of the number of input states. */
    private val inputStateHistogram = metrics.histogram("$metricPrefix.NumberOfInputStates")
    /** Track the measured ETA. */
    private val requestProcessingETA = metrics.histogram("$metricPrefix.requestProcessingETASeconds")
    /** Track the number of requests in the queue at insert. */
    private val requestQueueCount = metrics.histogram("$metricPrefix.requestQueue.size")
    /** Track the number of states in the queue at insert. */
    private val requestQueueStateCount = metrics.histogram("$metricPrefix.requestQueue.queuedStates")
    /** Tracks the distribution of the number of unique transactions that contributed states to the current transaction */
    private val uniqueTxHashCount = metrics.histogram("$metricPrefix.NumberOfUniqueTxHashes")
    /** Track the batch size. */
    private val processedBatchSize = metrics.histogram("$metricPrefix.ProcessedBatchSize")

    private val dataSource: DataSource = HibernateEntityManagerFactoryProvider.createDataSource(config.dataSource, metrics)
    private val entityManagerFactory = HibernateEntityManagerFactoryProvider.createEntityManagerFactory(dataSource, config.dataSource, config.databaseConfig, config.maxBatchSize)

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

    private fun decrementQueueSize(requests: List<CommitRequest>): Int {
        val nrStates = requests.map { it.states.size + it.references.size }.sum()
        nrQueuedStates.addAndGet(-nrStates)
        return nrStates
    }

    /** A requestEntitiy processor thread. */
    private val processorThread = thread(name = "Notary request queue processor", isDaemon = true) {
        try {
            val buffer = LinkedList<CommitRequest>()
            while (!Thread.interrupted()) {
                val drainedSize = Queues.drain(requestQueue, buffer, config.maxBatchSize, config.batchTimeoutMs, TimeUnit.MILLISECONDS)
                if (drainedSize == 0) continue
                processRequests(buffer)
                buffer.clear()
            }
        } catch (e: InterruptedException) {
        }
        log.debug { "Shutting down with ${requestQueue.size} in-flight requests unprocessed." }
    }

    fun stop() {
        entityManagerFactory.close()
        processorThread.interrupt()
    }

    companion object {
        private const val requestQueueSize = 100_000
        private val log = contextLogger()

        fun encodeStateRef(s: StateRef): String {
            return s.txhash.toString() + ":" + s.index.toString(16)
        }

        fun decodeStateRef(s: String): StateRef {
            return StateRef(txhash = SecureHash.parse(s.take(64)), index = s.substring(65).toInt(16))
        }
    }

    /**
     * Generates and adds a [CommitRequest] to the requestEntitiy queue. If the requestEntitiy queue is full, this method will block
     * until space is available.
     *
     * Returns a future that will complete once the requestEntitiy is processed, containing the commit [Result].
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
        uniqueTxHashCount.update(states.distinctBy { it.txhash }.count())
        val timer = Stopwatch.createStarted()
        val future = openFuture<UniquenessProvider.Result>()
        val requestEntities = Request(consumingTxHash = txId.toString(),
                partyName = callerIdentity.name.toString(),
                requestSignature = requestSignature.serialize(context = SerializationDefaults.STORAGE_CONTEXT.withEncoding(CordaSerializationEncoding.SNAPPY)).bytes,
                requestDate = clock.instant())
        val stateEntities = states.map { CommittedState(encodeStateRef(it), txId.toString()) }
        val request = CommitRequest(states, txId, callerIdentity, requestSignature, timeWindow, references, future, requestEntities, stateEntities)
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

    // Safe up to 100k requests per second.
    private var nextRequestId = System.currentTimeMillis() * 100

    private fun logRequests(requests: List<CommitRequest>, entityManager: EntityManager) {
        for (request in requests) {
            request.requestEntity.id = instanceId + (nextRequestId++).toString(16)
            entityManager.persist(request.requestEntity)
        }
    }

    private fun commitRequests(requests: List<CommitRequest>, entityManager: EntityManager) {
        for (request in requests) {
            for (cs in request.committedStatesEntities) {
                entityManager.persist(cs)
            }
            entityManager.persist(CommittedTransaction(request.txId.toString()))
        }
    }

    private fun findAlreadyCommitted(states: List<StateRef>, references: List<StateRef>, entityManager: EntityManager): Map<StateRef, StateConsumptionDetails> {
        val ids = (states + references).map { encodeStateRef(it) }.toSet()
        val committedStates = mutableListOf<CommittedState>()

        for (idsBatch in ids.chunked(config.maxBatchInputStates)) {
            @SuppressWarnings("unchecked")
            val existing = entityManager.createNamedQuery("CommittedState.select", CommittedState::class.java).setParameter("ids", idsBatch).resultList as List<CommittedState>
            committedStates.addAll(existing)
        }

        return committedStates.map {
            val stateRef = decodeStateRef(it.id)
            val consumingTxId = SecureHash.parse(it.consumingTxHash)
            if (stateRef in references) {
                stateRef to StateConsumptionDetails(consumingTxId.sha256(), type = StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE)
            } else {
                stateRef to StateConsumptionDetails(consumingTxId.sha256())
            }
        }.toMap()
    }

    /** Runs the provided [operation], retrying on transient database errors. */
    private fun withRetry(block: () -> Unit, transaction: EntityTransaction) {
        var retryCount = 0
        sameConnection@ while (retryCount < config.maxDBTransactionRetryCount) {
            retryCount++
            try {
                block()
                return
            } catch (e: Exception) {
                when (e) {
                    is BatchUpdateException, // Occurs when a competing transaction commits conflicting input states
                    is SQLTransactionRollbackException -> {
                        log.warn("Database transaction conflict, retrying", e)
                        transaction.rollback()
                        rollbackCounter.inc()
                        continue@sameConnection // Retrying using the same connection
                    }
                    is SQLRecoverableException, // Occurs when an issue is encountered during execute() (e.g. connection lost)
                    is SQLNonTransientConnectionException -> { // Occurs when an issue is encountered during commit() (e.g. connection lost)
                        log.warn("Lost connection to the database, retrying", e)
                        break@sameConnection // Retrying using a new connection
                        // TODO: don't reinsert notarisation request on retry
                    }
                    else -> {
                        log.warn("Unexpected error occurred, attempting to rollback", e)
                        transaction.rollback()
                        throw e
                    }
                }
            }
        }
        throw IllegalStateException("Database operation reached the maximum number of retries: $retryCount, something went wrong.")
    }

    private fun findAllConflicts(requests: List<CommitRequest>, entityManager: EntityManager): MutableMap<StateRef, StateConsumptionDetails> {
        val allInputs = requests.flatMap { it.states }
        val references = requests.flatMap { it.references }
        log.info("Processing notarization requests with ${allInputs.size} input states and ${references.size} references")

        return findAlreadyCommitted(allInputs, references, entityManager).toMutableMap()
    }

    private fun processRequest(request: CommitRequest, allConflicts: MutableMap<StateRef, StateConsumptionDetails>, toCommit: MutableList<CommitRequest>, entityManager: EntityManager): UniquenessProvider.Result {
        val conflicts = (request.states + request.references).filter { allConflicts.containsKey(it) }.associate { it to allConflicts[it]!!}
        return if (conflicts.isNotEmpty()) {
            if (isConsumedByTheSameTx(request.txId.sha256(), conflicts)) {
                UniquenessProvider.Result.Success
            } else {
                if (request.states.isEmpty() && isPreviouslyNotarised(request.txId, entityManager)) {
                    UniquenessProvider.Result.Success
                } else {
                    conflictCounter.inc()
                    UniquenessProvider.Result.Failure(NotaryError.Conflict(request.txId, conflicts))
                }
            }
        } else {
            val outsideTimeWindowError = validateTimeWindow(clock.instant(), request.timeWindow)
            if (outsideTimeWindowError == null) {
                toCommit.add(request)
                // Mark states as consumed to capture conflicting transactions in the same batch.
                request.states.forEach {
                    allConflicts[it] = StateConsumptionDetails(request.txId.sha256())
                }
                UniquenessProvider.Result.Success
            } else {
                if (request.states.isEmpty() && isPreviouslyNotarised(request.txId, entityManager)) {
                    UniquenessProvider.Result.Success
                } else {
                    UniquenessProvider.Result.Failure(outsideTimeWindowError)
                }
            }
        }
    }

    private fun isPreviouslyNotarised(txId: SecureHash, entityManager: EntityManager): Boolean {
        return entityManager.find(CommittedTransaction::class.java, txId.toString()) != null
    }

    private fun processRequests(buffer: LinkedList<CommitRequest>) {
        val numStates = decrementQueueSize(buffer)
        var inputStateCount = 0
        val batch = LinkedList<CommitRequest>()
        val duration = elapsedTime {

            // Note that there is an additional retry mechanism within the transaction itself.
            while (buffer.isNotEmpty()) {
                while (buffer.isNotEmpty() && inputStateCount + buffer.peek().states.size <= config.maxBatchInputStates) {
                    val request = buffer.poll()
                    batch.add(request)
                }
                try {
                    val entityManager = entityManagerFactory.createEntityManager()
                    entityManager.transaction.begin()

                    inputStateCount = processBatch(batch, entityManager)
                    processedBatchSize.update(batch.size)

                    inputStatesMeter.mark(inputStateCount.toLong())
                    inputStateCount = 0

                    entityManager.transaction.commit()
                    entityManager.flush()
                    entityManager.clear()
                } catch (e: Exception) {
                    log.warn("Error processing commit requests", e)
                    for (request in batch) {
                        respondWithError(request, e)
                    }
                }
            }

        }
        val statesPerMinute = numStates.toLong() * TimeUnit.MINUTES.toNanos(1) / duration.toNanos()
        throughputHistory.update(maxOf(statesPerMinute, 1))
        throughput = throughputHistory.snapshot.median // Median deemed more stable / representative than mean.
    }

    private fun processBatch(batch: LinkedList<CommitRequest>, entityManager: EntityManager): Int {
        var inputStateCount = 0
        val toCommit = mutableListOf<CommitRequest>()
        val allConflicts = findAllConflicts(batch, entityManager)

        val results = batch.map { request ->
            processRequest(request, allConflicts, toCommit, entityManager)
        }

        withRetry({
            logRequests(batch, entityManager)
            commitRequests(toCommit, entityManager)
        }, entityManager.transaction)

        for ((request, result) in batch.zip(results)) {
            request.future.set(result)
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
