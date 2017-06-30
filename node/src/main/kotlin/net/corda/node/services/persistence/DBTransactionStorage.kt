package net.corda.node.services.persistence

import com.google.common.annotations.VisibleForTesting
import net.corda.core.bufferUntilSubscribed
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.TransactionStorage
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.node.utilities.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.InsertStatement
import rx.Observable
import rx.subjects.PublishSubject
import java.util.Collections.synchronizedMap

class DBTransactionStorage : TransactionStorage, SingletonSerializeAsToken() {
    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}transactions") {
        val txId = secureHash("tx_id")
        val transaction = blob("transaction")
    }

    private class TransactionsMap : AbstractJDBCHashMap<SecureHash, SignedTransaction, Table>(Table, loadOnInit = false) {
        override fun keyFromRow(row: ResultRow): SecureHash = row[table.txId]

        override fun valueFromRow(row: ResultRow): SignedTransaction = deserializeFromBlob(row[table.transaction])

        override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, SignedTransaction>, finalizables: MutableList<() -> Unit>) {
            insert[table.txId] = entry.key
        }

        override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<SecureHash, SignedTransaction>, finalizables: MutableList<() -> Unit>) {
            insert[table.transaction] = serializeToBlob(entry.value, finalizables)
        }
    }

    private val txStorage = synchronizedMap(TransactionsMap())

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        val recorded = synchronized(txStorage) {
            val old = txStorage[transaction.id]
            if (old == null) {
                txStorage.put(transaction.id, transaction)
                updatesPublisher.bufferUntilDatabaseCommit().onNext(transaction)
                true
            } else {
                false
            }
        }
        if (!recorded) {
            exposedLogger.warn("Duplicate recording of transaction ${transaction.id}")
        }
        return recorded
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? {
        synchronized(txStorage) {
            return txStorage[id]
        }
    }

    private val updatesPublisher = PublishSubject.create<SignedTransaction>().toSerialized()
    override val updates: Observable<SignedTransaction> = updatesPublisher.wrapWithDatabaseTransaction()

    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        synchronized(txStorage) {
            return DataFeed(txStorage.values.toList(), updatesPublisher.bufferUntilSubscribed().wrapWithDatabaseTransaction())
        }
    }

    @VisibleForTesting
    val transactions: Iterable<SignedTransaction> get() = synchronized(txStorage) {
        txStorage.values.toList()
    }
}
