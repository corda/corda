package net.corda.node.utilities

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

import rx.Observable
import rx.Subscriber
import rx.subjects.UnicastSubject
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class CordaPersistence(var dataSource: HikariDataSource): Closeable {

    /** Holds Exposed database, the field will be removed once Exposed is phased out */
    lateinit var database: Database

    fun createTransaction(): DatabaseTransaction {
        // We need to set the database for the current [Thread] or [Fiber] here as some tests share threads across databases.
        DatabaseTransactionManager.dataSource = this
        return DatabaseTransactionManager.currentOrNew(Connection.TRANSACTION_REPEATABLE_READ)
    }

    @Deprecated("Use CordaPersistence.transaction instead")
    fun <T> databaseTransaction(db: CordaPersistence, statement: DatabaseTransaction.() -> T) = db.transaction(statement)

    fun <T> isolatedTransaction(block: DatabaseTransaction.() -> T): T {
        val context = DatabaseTransactionManager.setThreadLocalTx(null)
        return try {
            transaction(block)
        } finally {
            DatabaseTransactionManager.restoreThreadLocalTx(context)
        }
    }

    fun <T> transaction(statement: DatabaseTransaction.() -> T): T {
        DatabaseTransactionManager.dataSource = this
        return transaction(Connection.TRANSACTION_REPEATABLE_READ, 3, statement)
    }

    private fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, statement: DatabaseTransaction.() -> T): T {
        val outer = DatabaseTransactionManager.currentOrNull()

        return if (outer != null) {
            outer.statement()
        }
        else {
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
            }
            catch (e: SQLException) {
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

    override fun close() {
        dataSource.close()
    }
}

fun configureDatabase(props: Properties): CordaPersistence {
    val config = HikariConfig(props)
    val dataSource = HikariDataSource(config)
    val persistence = CordaPersistence(dataSource)
    DatabaseTransactionManager(persistence)
    val database = Database.connect(dataSource) { db -> ExposedTransactionManager() } //org.jetbrains.exposed.sql.Database will be removed once Exposed is phased out
    persistence.database = database

    // Check not in read-only mode.
    persistence.transaction {
        check(!database.metadata.isReadOnly) { "Database should not be readonly." }
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

    override fun onCompleted() {
        forEachSubscriberWithDbTx { onCompleted() }
    }

    override fun onError(e: Throwable?) {
        forEachSubscriberWithDbTx { onError(e) }
    }

    override fun onNext(s: U) {
        forEachSubscriberWithDbTx { onNext(s) }
    }

    override fun onStart() {
        forEachSubscriberWithDbTx { onStart() }
    }

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
    val wrappingSubscriber = DatabaseTransactionWrappingSubscriber<T>(db)
    // Use lift to add subscribers to a special subscriber that wraps a database transaction around observations.
    // Each subscriber will be passed to this lambda when they subscribe, at which point we add them to wrapping subscriber.
    return this.lift { toBeWrappedInDbTx: Subscriber<in T> ->
        // Add the subscriber to the wrapping subscriber, which will invoke the original subscribers together inside a database transaction.
        wrappingSubscriber.delegates.add(toBeWrappedInDbTx)
        // If we are the first subscriber, return the shared subscriber, otherwise return a subscriber that does nothing.
        if (wrappingSubscriber.delegates.size == 1) wrappingSubscriber else NoOpSubscriber(toBeWrappedInDbTx)
        // Clean up the shared list of subscribers when they unsubscribe.
    }.doOnUnsubscribe { wrappingSubscriber.cleanUp() }
}
