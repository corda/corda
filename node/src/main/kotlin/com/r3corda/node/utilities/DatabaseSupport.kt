package com.r3corda.node.utilities

import co.paralleluniverse.strands.Strand
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.io.Closeable
import java.sql.Connection
import java.util.*

// TODO: Handle commit failure due to database unavailable.  Better to shutdown and await database reconnect/recovery.
fun <T> databaseTransaction(db: Database, statement: Transaction.() -> T): T {
    // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
    StrandLocalTransactionManager.database = db
    return org.jetbrains.exposed.sql.transactions.transaction(Connection.TRANSACTION_REPEATABLE_READ, 1, statement)
}

fun createDatabaseTransaction(db: Database): Transaction {
    // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
    StrandLocalTransactionManager.database = db
    return TransactionManager.currentOrNew(Connection.TRANSACTION_REPEATABLE_READ)
}

fun configureDatabase(props: Properties): Pair<Closeable, Database> {
    val config = HikariConfig(props)
    val dataSource = HikariDataSource(config)
    val database = Database.connect(dataSource) { db -> StrandLocalTransactionManager(db) }
    // Check not in read-only mode.
    databaseTransaction(database) {
        check(!database.metadata.isReadOnly) { "Database should not be readonly." }
    }
    return Pair(dataSource, database)
}

/**
 * A relatively close copy of the [ThreadLocalTransactionManager] in Exposed but with the following adjustments to suit
 * our environment:
 *
 * Because the construction of a [Database] instance results in replacing the singleton [TransactionManager] instance,
 * our tests involving two [MockNode]s effectively replace the database instances of each other and continue to trample
 * over each other.  So here we use a companion object to hold them as [ThreadLocal] and [StrandLocalTransactionManager]
 * is otherwise effectively stateless so it's replacement does not matter.  The [ThreadLocal] is then set correctly and
 * explicitly just prior to initiating a transaction in [databaseTransaction] and [createDatabaseTransaction] above.
 */
class StrandLocalTransactionManager(initWithDatabase: Database) : TransactionManager {

    companion object {
        private val threadLocalDb = ThreadLocal<Database>()
        private val threadLocalTx = ThreadLocal<Transaction>()

        var database: Database
            get() = threadLocalDb.get() ?: throw IllegalStateException("Was expecting to find database set on current strand: ${Strand.currentStrand()}")
            set(value: Database) {
                threadLocalDb.set(value)
            }
    }

    init {
        database = initWithDatabase
        // Found a unit test that was forgetting to close the database transactions.  When you close() on the top level
        // database transaction it will reset the threadLocalTx back to null, so if it isn't then there is still a
        // databae transaction open.  The [databaseTransaction] helper above handles this in a finally clause for you
        // but any manual database transaction management is liable to have this problem.
        if (threadLocalTx.get() != null) {
            throw IllegalStateException("Was not expecting to find existing database transaction on current strand when setting database: ${Strand.currentStrand()}, ${threadLocalTx.get()}")
        }
    }

    override fun newTransaction(isolation: Int): Transaction = Transaction(StrandLocalTransaction(database, isolation, threadLocalTx)).apply {
        threadLocalTx.set(this)
    }

    override fun currentOrNull(): Transaction? = threadLocalTx.get()

    // Direct copy of [ThreadLocalTransaction].
    private class StrandLocalTransaction(override val db: Database, isolation: Int, val threadLocal: ThreadLocal<Transaction>) : TransactionInterface {

        override val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
            db.connector().apply {
                autoCommit = false
                transactionIsolation = isolation
            }
        }

        override val outerTransaction = threadLocal.get()

        override fun commit() {
            connection.commit()
        }

        override fun rollback() {
            if (!connection.isClosed) {
                connection.rollback()
            }
        }

        override fun close() {
            connection.close()
            threadLocal.set(outerTransaction)
        }

    }
}