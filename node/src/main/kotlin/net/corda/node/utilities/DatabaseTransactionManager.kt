package net.corda.node.utilities

import co.paralleluniverse.strands.Strand
import rx.subjects.PublishSubject
import rx.subjects.Subject
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class DatabaseTransaction(isolation: Int, val threadLocal: ThreadLocal<DatabaseTransaction>,
                          val transactionBoundaries: Subject<DatabaseTransactionManager.Boundary, DatabaseTransactionManager.Boundary>,
                          val cordaPersistence: CordaPersistence) {

    val id: UUID = UUID.randomUUID()

    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        cordaPersistence.dataSource.connection
                .apply {
                    autoCommit = false
                    transactionIsolation = isolation
                }
    }

    val outerTransaction: DatabaseTransaction? = threadLocal.get()

    fun commit() {
        connection.commit()
    }

    fun rollback() {
        if (!connection.isClosed) {
            connection.rollback()
        }
    }

    fun close() {
        connection.close()
        threadLocal.set(outerTransaction)
        if (outerTransaction == null) {
            transactionBoundaries.onNext(DatabaseTransactionManager.Boundary(id))
        }
    }
}


class DatabaseTransactionManager(initDataSource: CordaPersistence) {

    companion object {

        private val threadLocalDb = ThreadLocal<CordaPersistence>()
        private val threadLocalTx = ThreadLocal<DatabaseTransaction>()
        private val databaseToInstance = ConcurrentHashMap<CordaPersistence, DatabaseTransactionManager>()

        fun setThreadLocalTx(tx: DatabaseTransaction?): DatabaseTransaction? {
            val oldTx = threadLocalTx.get()
            threadLocalTx.set(tx)
            return oldTx
        }

        fun restoreThreadLocalTx(context: DatabaseTransaction?) {
            if (context != null) {
                threadLocalDb.set(context.cordaPersistence)
            }
            threadLocalTx.set(context)
        }

        var dataSource: CordaPersistence
            get() = threadLocalDb.get() ?: throw IllegalStateException("Was expecting to find CordaPersistence set on current thread: ${Strand.currentStrand()}")
            set(value) = threadLocalDb.set(value)

        val transactionId: UUID
            get() = threadLocalTx.get()?.id ?: throw IllegalStateException("Was expecting to find transaction set on current strand: ${Strand.currentStrand()}")

        val manager: DatabaseTransactionManager get() = databaseToInstance[dataSource]!!

        val transactionBoundaries: Subject<Boundary, Boundary> get() = manager._transactionBoundaries

        fun currentOrNull(): DatabaseTransaction? = manager.currentOrNull()

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

        fun current(): DatabaseTransaction = currentOrNull() ?: error("No transaction in context.")

        fun newTransaction(isolation: Int) = manager.newTransaction(isolation)
    }

    data class Boundary(val txId: UUID)

    private val _transactionBoundaries = PublishSubject.create<Boundary>().toSerialized()

    init {
        // Found a unit test that was forgetting to close the database transactions.  When you close() on the top level
        // database transaction it will reset the threadLocalTx back to null, so if it isn't then there is still a
        // database transaction open.  The [transaction] helper above handles this in a finally clause for you
        // but any manual database transaction management is liable to have this problem.
        if (threadLocalTx.get() != null) {
            throw IllegalStateException("Was not expecting to find existing database transaction on current strand when setting database: ${Strand.currentStrand()}, ${threadLocalTx.get()}")
        }
        dataSource = initDataSource
        databaseToInstance[dataSource] = this
    }

    private fun newTransaction(isolation: Int): DatabaseTransaction {
        val tx = DatabaseTransaction(isolation, threadLocalTx, transactionBoundaries, dataSource)
        tx.apply {
            threadLocalTx.set(this)
        }
        return tx
    }

    private fun currentOrNull(): DatabaseTransaction? = threadLocalTx.get()
}
