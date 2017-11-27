package net.corda.node.utilities

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.core.node.services.IdentityService
import net.corda.node.services.api.SchemaService
import net.corda.node.services.persistence.HibernateConfiguration
import net.corda.node.services.schema.NodeSchemaService
import rx.Observable
import rx.Subscriber
import rx.subjects.UnicastSubject
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Table prefix for all tables owned by the node module.
 */
const val NODE_DATABASE_PREFIX = "node_"

//HikariDataSource implements Closeable which allows CordaPersistence to be Closeable
class CordaPersistence(var dataSource: HikariDataSource, private val schemaService: SchemaService,
                       private val identityService: IdentityService, databaseProperties: Properties) : Closeable {
    var transactionIsolationLevel = parserTransactionIsolationLevel(databaseProperties.getProperty("transactionIsolationLevel"))

    val hibernateConfig: HibernateConfiguration by lazy {
        transaction {
            HibernateConfiguration(schemaService, databaseProperties, identityService)
        }
    }
    val entityManagerFactory get() = hibernateConfig.sessionFactoryForRegisteredSchemas

    companion object {
        fun connect(dataSource: HikariDataSource, schemaService: SchemaService, identityService: IdentityService, databaseProperties: Properties): CordaPersistence {
            return CordaPersistence(dataSource, schemaService, identityService, databaseProperties).apply {
                DatabaseTransactionManager(this)
            }
        }
    }

    /**
     * Creates an instance of [DatabaseTransaction], with the given isolation level.
     * @param isolationLevel isolation level for the transaction. If not specified the default (i.e. provided at the creation time) is used.
     */
    fun createTransaction(isolationLevel: Int): DatabaseTransaction {
        // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
        DatabaseTransactionManager.dataSource = this
        return DatabaseTransactionManager.currentOrNew(isolationLevel)
    }

    /**
     * Creates an instance of [DatabaseTransaction], with the transaction isolation level specified at the creation time.
     */
    fun createTransaction(): DatabaseTransaction {
        return createTransaction(transactionIsolationLevel)
    }

    fun createSession(): Connection {
        // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
        DatabaseTransactionManager.dataSource = this
        val ctx = DatabaseTransactionManager.currentOrNull()
        return ctx?.connection ?: throw IllegalStateException("Was expecting to find database transaction: must wrap calling code within a transaction.")
    }

    /**
     * Executes given statement in the scope of transaction, with the given isolation level.
     * @param isolationLevel isolation level for the transaction.
     * @param statement to be executed in the scope of this transaction.
     */
    fun <T> transaction(isolationLevel: Int, statement: DatabaseTransaction.() -> T): T {
        DatabaseTransactionManager.dataSource = this
        return transaction(isolationLevel, 3, statement)
    }

    /**
     * Executes given statement in the scope of transaction with the transaction level specified at the creation time.
     * @param statement to be executed in the scope of this transaction.
     */
    fun <T> transaction(statement: DatabaseTransaction.() -> T): T {
        return transaction(transactionIsolationLevel, statement)
    }

    private fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, statement: DatabaseTransaction.() -> T): T {
        val outer = DatabaseTransactionManager.currentOrNull()

        return if (outer != null) {
            outer.statement()
        } else {
            inTopLevelTransaction(transactionIsolation, repetitionAttempts, statement)
        }
    }

    private fun <T> inTopLevelTransaction(transactionIsolation: Int, repetitionAttempts: Int, statement: DatabaseTransaction.() -> T): T {
        var repetitions = 0
        while (true) {
            val transaction = DatabaseTransactionManager.currentOrNew(transactionIsolation)
            try {
                val answer = transaction.statement()
                transaction.commit()
                return answer
            } catch (e: SQLException) {
                transaction.rollback()
                repetitions++
                if (repetitions >= repetitionAttempts) {
                    throw e
                }
            } catch (e: Throwable) {
                transaction.rollback()
                throw e
            } finally {
                transaction.close()
            }
        }
    }

    override fun close() {
        dataSource.close()
    }
}

fun configureDatabase(dataSourceProperties: Properties, databaseProperties: Properties?, identityService: IdentityService, schemaService: SchemaService = NodeSchemaService(null)): CordaPersistence {
    val config = HikariConfig(dataSourceProperties)
    val dataSource = HikariDataSource(config)
    val persistence = CordaPersistence.connect(dataSource, schemaService, identityService, databaseProperties ?: Properties())
    // Check not in read-only mode.
    persistence.transaction {
        persistence.dataSource.connection.use {
            check(!it.metaData.isReadOnly) { "Database should not be readonly." }
        }
    }
    return persistence
}

/**
 * Buffer observations until after the current database transaction has been closed.  Observations are never
 * dropped, simply delayed.
 *
 * Primarily for use by component authors to publish observations during database transactions without racing against
 * closing the database transaction.
 *
 * For examples, see the call hierarchy of this function.
 */
fun <T : Any> rx.Observer<T>.bufferUntilDatabaseCommit(): rx.Observer<T> {
    val currentTxId = DatabaseTransactionManager.transactionId
    val databaseTxBoundary: Observable<DatabaseTransactionManager.Boundary> = DatabaseTransactionManager.transactionBoundaries.filter { it.txId == currentTxId }.first()
    val subject = UnicastSubject.create<T>()
    subject.delaySubscription(databaseTxBoundary).subscribe(this)
    databaseTxBoundary.doOnCompleted { subject.onCompleted() }
    return subject
}

// A subscriber that delegates to multiple others, wrapping a database transaction around the combination.
private class DatabaseTransactionWrappingSubscriber<U>(val db: CordaPersistence?) : Subscriber<U>() {
    // Some unsubscribes happen inside onNext() so need something that supports concurrent modification.
    val delegates = CopyOnWriteArrayList<Subscriber<in U>>()

    fun forEachSubscriberWithDbTx(block: Subscriber<in U>.() -> Unit) {
        (db ?: DatabaseTransactionManager.dataSource).transaction {
            delegates.filter { !it.isUnsubscribed }.forEach {
                it.block()
            }
        }
    }

    override fun onCompleted() = forEachSubscriberWithDbTx { onCompleted() }

    override fun onError(e: Throwable?) = forEachSubscriberWithDbTx { onError(e) }

    override fun onNext(s: U) = forEachSubscriberWithDbTx { onNext(s) }

    override fun onStart() = forEachSubscriberWithDbTx { onStart() }

    fun cleanUp() {
        if (delegates.removeIf { it.isUnsubscribed }) {
            if (delegates.isEmpty()) {
                unsubscribe()
            }
        }
    }
}

// A subscriber that wraps another but does not pass on observations to it.
private class NoOpSubscriber<U>(t: Subscriber<in U>) : Subscriber<U>(t) {
    override fun onCompleted() {
    }

    override fun onError(e: Throwable?) {
    }

    override fun onNext(s: U) {
    }
}

/**
 * Wrap delivery of observations in a database transaction.  Multiple subscribers will receive the observations inside
 * the same database transaction.  This also lazily subscribes to the source [rx.Observable] to preserve any buffering
 * that might be in place.
 */
fun <T : Any> rx.Observable<T>.wrapWithDatabaseTransaction(db: CordaPersistence? = null): rx.Observable<T> {
    var wrappingSubscriber = DatabaseTransactionWrappingSubscriber<T>(db)
    // Use lift to add subscribers to a special subscriber that wraps a database transaction around observations.
    // Each subscriber will be passed to this lambda when they subscribe, at which point we add them to wrapping subscriber.
    return this.lift { toBeWrappedInDbTx: Subscriber<in T> ->
        // Add the subscriber to the wrapping subscriber, which will invoke the original subscribers together inside a database transaction.
        wrappingSubscriber.delegates.add(toBeWrappedInDbTx)
        // If we are the first subscriber, return the shared subscriber, otherwise return a subscriber that does nothing.
        if (wrappingSubscriber.delegates.size == 1) wrappingSubscriber else NoOpSubscriber(toBeWrappedInDbTx)
        // Clean up the shared list of subscribers when they unsubscribe.
    }.doOnUnsubscribe {
        wrappingSubscriber.cleanUp()
        // If cleanup removed the last subscriber reset the system, as future subscribers might need the stream again
        if (wrappingSubscriber.delegates.isEmpty()) {
            wrappingSubscriber = DatabaseTransactionWrappingSubscriber(db)
        }
    }
}

fun parserTransactionIsolationLevel(property: String?): Int =
        when (property) {
            "none" -> Connection.TRANSACTION_NONE
            "readUncommitted" -> Connection.TRANSACTION_READ_UNCOMMITTED
            "readCommitted" -> Connection.TRANSACTION_READ_COMMITTED
            "repeatableRead" -> Connection.TRANSACTION_REPEATABLE_READ
            "serializable" -> Connection.TRANSACTION_SERIALIZABLE
            else -> {
                Connection.TRANSACTION_REPEATABLE_READ
            }
        }
