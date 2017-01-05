package net.corda.node.services.persistence

import net.corda.core.ThreadBox
import net.corda.core.bufferUntilSubscribed
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.StateMachineRunId
import net.corda.core.node.services.StateMachineRecordedTransactionMappingStorage
import net.corda.core.node.services.StateMachineTransactionMapping
import net.corda.node.utilities.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import rx.Observable
import rx.subjects.PublishSubject
import javax.annotation.concurrent.ThreadSafe

/**
 * Database storage of a txhash -> state machine id mapping.
 *
 * Mappings are added as transactions are persisted by [ServiceHub.recordTransaction], and never deleted.  Used in the
 * RPC API to correlate transaction creation with flows.
 *
 */
@ThreadSafe
class DBTransactionMappingStorage : StateMachineRecordedTransactionMappingStorage {

    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}transaction_mappings") {
        val txId = secureHash("tx_id")
        val stateMachineRunId = uuidString("state_machine_run_id")
    }

    private class TransactionMappingsMap : AbstractJDBCHashMap<SecureHash, StateMachineRunId, Table>(Table, loadOnInit = false) {
        override fun keyFromRow(row: ResultRow): SecureHash = row[table.txId]

        override fun valueFromRow(row: ResultRow): StateMachineRunId = StateMachineRunId.wrap(row[table.stateMachineRunId])

        override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, StateMachineRunId>, finalizables: MutableList<() -> Unit>) {
            insert[table.txId] = entry.key
        }

        override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, StateMachineRunId>, finalizables: MutableList<() -> Unit>) {
            insert[table.stateMachineRunId] = entry.value.uuid
        }
    }

    private val mutex = ThreadBox(object {
        val stateMachineTransactionMap = TransactionMappingsMap()
        val updates = PublishSubject.create<StateMachineTransactionMapping>()
    })

    override fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash) {
        mutex.locked {
            stateMachineTransactionMap[transactionId] = stateMachineRunId
            updates.bufferUntilDatabaseCommit().onNext(StateMachineTransactionMapping(stateMachineRunId, transactionId))
        }
    }

    override fun track(): Pair<List<StateMachineTransactionMapping>, Observable<StateMachineTransactionMapping>> {
        mutex.locked {
            return Pair(
                    stateMachineTransactionMap.map { StateMachineTransactionMapping(it.value, it.key) },
                    updates.bufferUntilSubscribed().wrapWithDatabaseTransaction()
            )
        }
    }
}
