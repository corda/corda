package net.corda.node.services.persistence

import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.schemas.MappedSchema
import net.corda.node.services.api.StateMachineRecordedTransactionMappingStorage
import net.corda.node.utilities.*
import rx.subjects.PublishSubject
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

    object TransactionMappingSchema

    object TransactionMappingSchemaV1 : MappedSchema(schemaFamily = TransactionMappingSchema.javaClass, version = 1,
            mappedTypes = listOf(Transaction::class.java)) {

        @Entity
        @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}transaction_mappings")
        class Transaction(
                @Id
                @Column(name = "tx_id", length = 64)
                var txId: String = "",

                @Column(name = "state_machine_run_id", length = 36)
                var stateMachineRunId: String = ""
        )
    }

    private companion object {
        fun createTransactionMappingMap(): AppendOnlyPersistentMap<SecureHash, StateMachineRunId, TransactionMappingSchemaV1.Transaction, String> {
            return AppendOnlyPersistentMap(
                    cacheBound = 1024,
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = { Pair(SecureHash.parse(it.txId), StateMachineRunId(UUID.fromString(it.stateMachineRunId))) },
                    toPersistentEntity = { key: SecureHash, value: StateMachineRunId ->
                        TransactionMappingSchemaV1.Transaction().apply {
                            txId = key.toString()
                            stateMachineRunId = value.uuid.toString()
                        }
                    },
                    persistentEntityClass = TransactionMappingSchemaV1.Transaction::class.java
            )
        }
    }

    val stateMachineTransactionMap = createTransactionMappingMap()
    val updates: PublishSubject<StateMachineTransactionMapping> = PublishSubject.create()

    override fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash) {
        stateMachineTransactionMap[transactionId] = stateMachineRunId
        updates.bufferUntilDatabaseCommit().onNext(StateMachineTransactionMapping(stateMachineRunId, transactionId))
    }

    override fun track(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> =
            DataFeed(stateMachineTransactionMap.allPersisted().map { StateMachineTransactionMapping(it.second, it.first) }.toList(),
                    updates.bufferUntilSubscribed().wrapWithDatabaseTransaction())

}
