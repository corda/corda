package net.corda.node.utilities

import co.paralleluniverse.strands.Strand
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import rx.subjects.PublishSubject
import rx.subjects.Subject
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource


class CordaTransaction(isolation: Int, val threadLocal: ThreadLocal<CordaTransaction>, val connector: () -> Connection/*val datasource: DataSource,*//* */) {

    //val id = UUID.randomUUID()

    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        //println(" =============con ${Thread.currentThread().id } trans=${TransactionManager.manager.currentOrNull()} outerConn=$outerTransaction")
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
        //println(" strand close thread=${Thread.currentThread().id} me=${this.threadLocal.get()} outerTrans=$outerTransaction ")
        println("     thread=${Thread.currentThread().id}  ${threadLocal.get()} <- $outerTransaction (close)")
        connection.close()
        threadLocal.set(outerTransaction)
        //if (outerTransaction == null) {
        //transactionBoundaries.onNext(StrandLocalTransactionManager.Boundary(id))
        //} else {
        //println("outer " + outerTransaction.outerTransaction)
        //}
    }
}


class TransactionTracker(initDataSource: DataSource){
    companion object {
        // private val TX_ID = Key<UUID>()

        private val threadLocalDb = ThreadLocal<DataSource>()
        private val threadLocalTx = ThreadLocal<CordaTransaction>()
        private val databaseToInstance = ConcurrentHashMap<DataSource, TransactionTracker>()

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

        val manager: TransactionTracker get() = databaseToInstance[database]!!

        val transactionBoundaries: Subject<Boundary, Boundary> get() = manager._transactionBoundaries

        fun currentOrNull(): CordaTransaction? = manager.currentOrNull()

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)
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
        //val t = CordaTransaction(/*(database::getConnection),*/ isolation, threadLocalTx)
        val t = CordaTransaction(isolation, threadLocalTx) { database.connection!! }
        t.apply {
            threadLocalTx.set(this) //TODO commented out intentionally
            //putUserData(TX_ID, impl.id)
            println("  thread=${Thread.currentThread().id}: $this onDs=${database}")
        }
        //println(" =============new ${Thread.currentThread().id } trans=$t outerConn=$outer")
        //println(" strand transaction new thread=${Thread.currentThread().id } trans=$t outerConn=$outer")
        return t
    }

    fun currentOrNull(): CordaTransaction? = threadLocalTx.get()


}

fun configureDatabase2(props: Properties): Pair<Closeable, CordaPersistence> {
    val config = HikariConfig(props)
    val dataSource = HikariDataSource(config)
    val persistence = CordaPersistence(dataSource)
    val database = Database.connect(dataSource) { db -> StrandLocalTransactionManager(db, TransactionTracker(dataSource)) }
    // Check not in read-only mode.
    persistence.transaction {
        check(!database.metadata.isReadOnly) { "Database should not be readonly." }
    }
    return Pair(dataSource, persistence)
}

class CordaPersistence(initWithDatabase: DataSource) {

    var dataSource: DataSource
    init {
        dataSource = initWithDatabase
    }

    @Deprecated("Use Database.transaction instead.")
    fun <T> databaseTransaction(db: CordaPersistence, statement: CordaTransaction.() -> T) = db.transaction(statement)

//    // TODO: Handle commit failure due to database unavailable.  Better to shutdown and await database reconnect/recovery.
//    fun <T> Database.transaction(statement: Transaction.() -> T): T {
//        // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
//        StrandLocalTransactionManager.database = this
//        println("thread=${Thread.currentThread().id} db change to $this")
//        return org.jetbrains.exposed.sql.transactions.transaction(Connection.TRANSACTION_REPEATABLE_READ, 1, statement)
//    }

    fun CordaPersistence.createTransaction(): CordaTransaction {
        // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
        //StrandLocalTransactionManager.database = this
        TransactionTracker.database = this.dataSource
        println("thread=${Thread.currentThread().id} db change to $this for create")
        return TransactionTracker.currentOrNew(Connection.TRANSACTION_REPEATABLE_READ)
    }

    fun <T> transaction(statement: CordaTransaction.() -> T): T {
        //return database.transaction(statement)
        TransactionTracker.database = this.dataSource
        return transaction(Connection.TRANSACTION_REPEATABLE_READ, 1, statement)
    }

    fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, statement: CordaTransaction.() -> T): T {
        val outer = TransactionTracker.currentOrNull()

        return if (outer != null) {
            outer.statement()
        }
        else {
            inTopLevelTransaction(transactionIsolation, repetitionAttempts, statement)
        }
    }

    fun <T> inTopLevelTransaction(transactionIsolation: Int, repetitionAttempts: Int, statement: CordaTransaction.() -> T): T {
        var repetitions = 0

        while (true) {

            val transaction = TransactionTracker.currentOrNew(transactionIsolation)

            try {
                val answer = transaction.statement()
                transaction.commit()
                return answer
            }
            catch (e: SQLException) {
                //exposedLogger.info("Transaction attempt #$repetitions: ${e.message}", e)
                transaction.rollback()
                repetitions++
                if (repetitions >= repetitionAttempts) {
                    throw e
                }
            }
            catch (e: Throwable) {
                transaction.rollback()
                throw e
            }
            finally {
                transaction.close()
            }
        }
    }
}