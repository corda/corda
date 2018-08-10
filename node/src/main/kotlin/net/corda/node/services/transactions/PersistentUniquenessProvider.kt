package net.corda.node.services.transactions

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
import net.corda.core.internal.notary.*
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.serialization.internal.CordaSerializationEncoding
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*
import kotlin.concurrent.thread

/** A RDBMS backed Uniqueness provider */
@ThreadSafe
class PersistentUniquenessProvider(val clock: Clock, val database: CordaPersistence) : AsyncUniquenessProvider, SingletonSerializeAsToken() {

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

    private data class CommitRequest(
            val states: List<StateRef>,
            val txId: SecureHash,
            val callerIdentity: Party,
            val requestSignature: NotarisationRequestSignature,
            val timeWindow: TimeWindow?,
            val references: List<StateRef>,
            val future: OpenFuture<AsyncUniquenessProvider.Result>)

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_committed_states")
    class CommittedState(id: PersistentStateRef, consumingTxHash: String) : BaseComittedState(id, consumingTxHash)

    private val commitLog = createMap()

    private val requestQueue = LinkedBlockingQueue<CommitRequest>(100_000)

    /** A request processor thread. */
    private val processorThread = thread(name = "Notary request queue processor", isDaemon = true) {
        try {
            processRequests()
        } catch (e: InterruptedException) {
        }
        log.debug { "Shutting down with ${requestQueue.size} in-flight requests unprocessed." }
    }

    companion object {
        private val log = contextLogger()
        fun createMap(): AppendOnlyPersistentMap<StateRef, SecureHash, CommittedState, PersistentStateRef> =
                AppendOnlyPersistentMap(
                        "PersistentUniquenessProvider_transactions",
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
    override fun commitAsync(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>
    ): CordaFuture<AsyncUniquenessProvider.Result> {
        val future = openFuture<AsyncUniquenessProvider.Result>()
        val request = CommitRequest(states, txId, callerIdentity, requestSignature, timeWindow, references, future)
        requestQueue.put(request)
        return future
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
                handleConflicts(txId, conflictingStates)
            } else {
                handleNoConflicts(timeWindow, states, txId, commitLog)
            }
        }
    }

    private fun handleConflicts(txId: SecureHash, conflictingStates: LinkedHashMap<StateRef, StateConsumptionDetails>) {
        if (isConsumedByTheSameTx(txId.sha256(), conflictingStates)) {
            log.debug { "Transaction $txId already notarised" }
            return
        } else {
            log.debug { "Failure, input states already committed: ${conflictingStates.keys}" }
            val conflictError = NotaryError.Conflict(txId, conflictingStates)
            throw NotaryInternalException(conflictError)
        }
    }

    private fun handleNoConflicts(timeWindow: TimeWindow?, states: List<StateRef>, txId: SecureHash, commitLog: AppendOnlyPersistentMap<StateRef, SecureHash, CommittedState, PersistentStateRef>) {
        val outsideTimeWindowError = validateTimeWindow(clock.instant(), timeWindow)
        if (outsideTimeWindowError == null) {
            states.forEach { stateRef ->
                commitLog[stateRef] = txId
            }
            log.debug { "Successfully committed all input states: $states" }
        } else {
            throw NotaryInternalException(outsideTimeWindowError)
        }
    }

    private fun processRequests() {
        while (!Thread.interrupted()) {
            processRequest(requestQueue.take())
        }
    }

    private fun processRequest(request: CommitRequest) {
        try {
            commitOne(request.states, request.txId, request.callerIdentity, request.requestSignature, request.timeWindow, request.references)
            respondWithSuccess(request)
        } catch (e: Exception) {
            log.warn("Error processing commit request", e)
            respondWithError(request, e)
        }
    }

    private fun respondWithError(request: CommitRequest, exception: Exception) {
            if (exception is NotaryInternalException) {
                request.future.set(AsyncUniquenessProvider.Result.Failure(exception.error))
            } else {
                request.future.setException(NotaryInternalException(NotaryError.General(Exception("Internal service error."))))
            }
    }

    private fun respondWithSuccess(request: CommitRequest) {
        request.future.set(AsyncUniquenessProvider.Result.Success)
    }
}
