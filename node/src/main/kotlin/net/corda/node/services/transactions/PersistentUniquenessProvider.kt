package net.corda.node.services.transactions

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.Party
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.internal.notary.isConsumedByTheSameTx
import net.corda.core.internal.notary.validateTimeWindow
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.io.Serializable
import java.time.Clock
import java.time.Instant
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*

/** A RDBMS backed Uniqueness provider */
@ThreadSafe
class PersistentUniquenessProvider(val clock: Clock) : UniquenessProvider, SingletonSerializeAsToken() {
    @MappedSuperclass
    class BaseComittedState(
            @EmbeddedId
            val id: PersistentStateRef,

            @Column(name = "consuming_transaction_id", nullable = true)
            val consumingTxHash: String?
    ) : Serializable

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
    ) : Serializable

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_committed_states")
    class CommittedState(id: PersistentStateRef, consumingTxHash: String) : BaseComittedState(id, consumingTxHash)

    private class InnerState {
        val commitLog = createMap()
    }

    private val mutex = ThreadBox(InnerState())

    companion object {
        private val log = contextLogger()
        fun createMap(): AppendOnlyPersistentMap<StateRef, SecureHash, CommittedState, PersistentStateRef> =
                AppendOnlyPersistentMap(
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

    override fun commit(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>
    ) {
        mutex.locked {
            logRequest(txId, callerIdentity, requestSignature)
            val conflictingStates = findAlreadyCommitted(states + references, commitLog)
            if (conflictingStates.isNotEmpty()) {
                handleConflicts(txId, conflictingStates)
            } else {
                handleNoConflicts(timeWindow, states, txId, commitLog)
            }
        }
    }

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature, timeWindow: TimeWindow?) {
        commit(states, txId, callerIdentity, requestSignature, timeWindow, emptyList())
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

    private fun findAlreadyCommitted(states: List<StateRef>, commitLog: AppendOnlyPersistentMap<StateRef, SecureHash, CommittedState, PersistentStateRef>): LinkedHashMap<StateRef, StateConsumptionDetails> {
        val conflictingStates = LinkedHashMap<StateRef, StateConsumptionDetails>()
        for (inputState in states) {
            val consumingTx = commitLog[inputState]
            if (consumingTx != null) conflictingStates[inputState] = StateConsumptionDetails(consumingTx.sha256())
        }
        return conflictingStates
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
}
