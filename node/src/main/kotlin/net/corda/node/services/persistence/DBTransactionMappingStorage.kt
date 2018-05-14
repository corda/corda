package net.corda.node.services.persistence

import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.node.services.api.StateMachineRecordedTransactionMappingStorage
import net.corda.node.utilities.*
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import rx.subjects.PublishSubject
import java.io.Serializable
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*

/**
 * Database storage of a txhash -> state machine id mapping.
 *
 * Mappings are added as transactions are persisted by [ServiceHub.recordTransaction], and never deleted.  Used in the
 * RPC API to correlate transaction creation with flows.
 */
@ThreadSafe
class DBTransactionMappingStorage : StateMachineRecordedTransactionMappingStorage {

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}transaction_mappings")
    class DBTransactionMapping(
            @Id
            @Column(name = "tx_id", length = 64)
            var txId: String = "",

            @Column(name = "state_machine_run_id", length = 36)
            var stateMachineRunId: String = ""
    ) : Serializable

    private companion object {
        fun createMap(): AppendOnlyPersistentMap<SecureHash, StateMachineRunId, DBTransactionMapping, String> {
            return AppendOnlyPersistentMap(
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = { Pair(SecureHash.parse(it.txId), StateMachineRunId(UUID.fromString(it.stateMachineRunId))) },
                    toPersistentEntity = { key: SecureHash, (uuid) ->
                        DBTransactionMapping().apply {
                            txId = key.toString()
                            stateMachineRunId = uuid.toString()
                        }
                    },
                    persistentEntityClass = DBTransactionMapping::class.java
            )
        }
    }

    val stateMachineTransactionMap = createMap()
    val updates: PublishSubject<StateMachineTransactionMapping> = PublishSubject.create()

    override fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash) {
        stateMachineTransactionMap.addWithDuplicatesAllowed(transactionId, stateMachineRunId)
        updates.bufferUntilDatabaseCommit().onNext(StateMachineTransactionMapping(stateMachineRunId, transactionId))
    }

    override fun track(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> =
            DataFeed(stateMachineTransactionMap.allPersisted().map { StateMachineTransactionMapping(it.second, it.first) }.toList(),
                    updates.bufferUntilSubscribed().wrapWithDatabaseTransaction())
}
