package net.corda.node.services.transactions

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryInternalException
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.Party
import net.corda.core.internal.ThreadBox
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
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
    open class BaseComittedState(
            @EmbeddedId
            val id: PersistentStateRef,

            @Column(name = "consuming_transaction_id")
            val consumingTxHash: String
    ) : Serializable

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_request_log")
    @CordaSerializable
    class Request(
            @Id
            @GeneratedValue
            val id: Int? = null,

            @Column(name = "consuming_transaction_id")
            val consumingTxHash: String,

            @Column(name = "requesting_party_name")
            var partyName: String,

            @Lob
            @Column(name = "request_signature")
            val requestSignature: ByteArray,

            @Column(name = "request_timestamp")
            var requestDate: Instant
    ) : Serializable

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_committed_states")
    class CommittedState(id: PersistentStateRef, consumingTxHash: String) : BaseComittedState(id, consumingTxHash)

    private class InnerState {
        val committedStates = createMap()
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
                                    ?: throw IllegalStateException("DB returned null SecureHash transactionId")
                            val index = it.id.index ?: throw IllegalStateException("DB returned null SecureHash index")
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

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature) {
        logRequest(txId, callerIdentity, requestSignature)
        val conflict = commitStates(states, txId)
        if (conflict != null) throw NotaryInternalException(NotaryError.Conflict(txId, conflict))
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

    private fun commitStates(states: List<StateRef>, txId: SecureHash): Map<StateRef, StateConsumptionDetails>? {
        val conflict = mutex.locked {
            val conflictingStates = LinkedHashMap<StateRef, SecureHash>()
            for (inputState in states) {
                val consumingTx = committedStates[inputState]
                if (consumingTx != null) conflictingStates[inputState] = consumingTx
            }
            if (conflictingStates.isNotEmpty()) {
                log.debug("Failure, input states already committed: ${conflictingStates.keys}")
                val conflict = conflictingStates.mapValues { (_, txId) -> StateConsumptionDetails(txId.sha256()) }
                conflict
            } else {
                states.forEach { stateRef ->
                    committedStates[stateRef] = txId
                }
                log.debug("Successfully committed all input states: $states")
                null
            }
        }
        return conflict
    }
}
