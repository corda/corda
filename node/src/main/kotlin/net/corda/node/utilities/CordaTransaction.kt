package net.corda.node.utilities

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import rx.subjects.PublishSubject
import rx.subjects.Subject
import java.io.Closeable
import java.sql.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource


class CordaTransaction(isolation: Int, val threadLocal: ThreadLocal<CordaTransaction>, val connector: () -> Connection) {

    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        println(" === connecting thread=${Thread.currentThread().id} me=${this.threadLocal.get()} this=$this")
        connector()
                .apply {
                    autoCommit = false
                    transactionIsolation = isolation
                }
    }

    val outerTransaction = threadLocal.get()

    fun commit() {
        connection.commit()
    }

    fun rollback() {
        if (!connection.isClosed) {
            connection.rollback()
        }
    }

    fun close() {
        println("     thread=${Thread.currentThread().id}  ${threadLocal.get()} <- $outerTransaction (close)")
        connection.close()
        threadLocal.set(outerTransaction)
    }
}


class CordaTransactionManager(initDataSource: DataSource){
    companion object {
        // private val TX_ID = Key<UUID>()

        private val threadLocalDb = ThreadLocal<DataSource>()
        private val threadLocalTx = ThreadLocal<CordaTransaction>()
        private val databaseToInstance = ConcurrentHashMap<DataSource, CordaTransactionManager>()

        fun setThreadLocalTx(tx: CordaTransaction?): Pair<DataSource?, CordaTransaction?> {
            val oldTx = threadLocalTx.get()
            threadLocalTx.set(tx)
            return Pair(threadLocalDb.get(), oldTx)
        }

        fun restoreThreadLocalTx(context: Pair<DataSource?, CordaTransaction?>) {
            threadLocalDb.set(context.first)
            threadLocalTx.set(context.second)
        }

        var database: DataSource
            get() = threadLocalDb.get() ?: throw IllegalStateException("Was expecting to find database set on current thread: ${Thread.currentThread().id}")
            set(value) {
                println("settingDB    thread=${Thread.currentThread().id} datasource=$value")
                threadLocalDb.set(value)
            }

        //val transactionId: UUID
        //    get() = threadLocalTx.get()?.getUserData(TX_ID) ?: throw IllegalStateException("Was expecting to find transaction set on current strand: ${Strand.currentStrand()}")

        val manager: CordaTransactionManager get() = databaseToInstance[database]!!

        val transactionBoundaries: Subject<Boundary, Boundary> get() = manager._transactionBoundaries

        fun currentOrNull(): CordaTransaction? = manager.currentOrNull()

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

        fun current(): CordaTransaction = currentOrNull() ?: error("No transaction in context.")
    }


    data class Boundary(val txId: UUID)

    private val _transactionBoundaries = PublishSubject.create<Boundary>().toSerialized()

    init {
        // Found a unit test that was forgetting to close the database transactions.  When you close() on the top level
        // database transaction it will reset the threadLocalTx back to null, so if it isn't then there is still a
        // databae transaction open.  The [transaction] helper above handles this in a finally clause for you
        // but any manual database transaction management is liable to have this problem.
        //if (threadLocalTx.get() != null) {
        //    throw IllegalStateException("Was not expecting to find existing database transaction on current strand when setting database: ${Strand.currentStrand()}, ${threadLocalTx.get()}")
        //}
        database = initDataSource
        databaseToInstance[database] = this
    }

    fun newTransaction(isolation: Int): CordaTransaction {
        //val outer = threadLocalTx.get()
        val t = CordaTransaction(isolation, threadLocalTx) { database.connection!! }
        t.apply {
            threadLocalTx.set(this)
            println("  thread=${Thread.currentThread().id}: $this onDs=${database}")
        }
        //println(" =============new ${Thread.currentThread().id } trans=$t outerConn=$outer")
        return t
    }

    fun currentOrNull(): CordaTransaction? = threadLocalTx.get()
}
