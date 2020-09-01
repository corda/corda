package net.corda.nodeapi.internal.persistence

import co.paralleluniverse.strands.Strand
import net.corda.core.CordaRuntimeException
import org.hibernate.Session
import org.hibernate.Transaction
import rx.subjects.PublishSubject
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.persistence.EntityManager

fun currentDBSession(): Session = contextTransaction.session

private val _contextTransaction = ThreadLocal<DatabaseTransaction>()
var contextTransactionOrNull: DatabaseTransaction?
    get() = if (_prohibitDatabaseAccess.get() == true) throw IllegalAccessException("Database access is disabled in this context.") else _contextTransaction.get()
    set(transaction) = _contextTransaction.set(transaction)
val contextTransaction
    get() = contextTransactionOrNull ?: error("Was expecting to find transaction set on current strand: ${Strand.currentStrand()}")

class DatabaseTransaction(
        isolation: Int,
        private val outerTransaction: DatabaseTransaction?,
        val database: CordaPersistence
) {
    val id: UUID = UUID.randomUUID()

    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        database.dataSource.connection.apply {
            autoCommit = false
            transactionIsolation = isolation
        }
    }

    private val sessionDelegate = lazy {
        val session = database.entityManagerFactory.withOptions().connection(connection).openSession()
        hibernateTransaction = session.beginTransaction()
        session
    }

    // Returns a delegate which overrides certain operations that we do not want CorDapp developers to call.

    val entityManager: EntityManager get() {
        // Always retrieve new session ([Session] implements [EntityManager])
        // Note, this does not replace the top level hibernate session
        val session = database.entityManagerFactory.withOptions().connection(connection).openSession()
        session.beginTransaction()
        return session
    }

    val session: Session by sessionDelegate
    private lateinit var hibernateTransaction: Transaction

    internal val boundary = PublishSubject.create<CordaPersistence.Boundary>()
    private var committed = false
    private var closed = false

    /**
     * Holds the first exception thrown from a series of statements executed in the same [DatabaseTransaction].
     * The purpose of this property is to make sure this exception cannot be suppressed in user code.
     * The exception will be thrown on the next [commit]. It is used only inside a flow state machine execution.
     */
    private var firstExceptionInDatabaseTransaction: Exception? = null

    fun setException(e: Exception) {
        if (firstExceptionInDatabaseTransaction == null) {
            firstExceptionInDatabaseTransaction = e
        }
    }

    private fun clearException() {
        firstExceptionInDatabaseTransaction = null
    }

    @Throws(SQLException::class)
    fun commit() {
        firstExceptionInDatabaseTransaction?.let {
            throw DatabaseTransactionException(it)
        }
        if (sessionDelegate.isInitialized()) {
            // The [sessionDelegate] must be initialised otherwise calling [entityManager] will cause an exception
            if(session.transaction.rollbackOnly) {
                throw RolledBackDatabaseSessionException()
            }
            hibernateTransaction.commit()
        }
        connection.commit()
        committed = true
    }

    @Throws(SQLException::class)
    fun rollback() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.clear()
        }
        if (!connection.isClosed) {
            connection.rollback()
        }
        clearException()
    }

    @Throws(SQLException::class)
    fun close() {
        try {
            if (sessionDelegate.isInitialized() && session.isOpen) {
                session.close()
            }
            if (database.closeConnection) {
                connection.close()
            }
        } finally {
            clearException()
            contextTransactionOrNull = outerTransaction
        }

        if (outerTransaction == null) {
            synchronized(this) {
                closed = true
                boundary.onNext(CordaPersistence.Boundary(id, committed))
            }
        }
    }

    fun onCommit(callback: () -> Unit) {
        boundary.filter { it.success }.subscribe { callback() }
    }

    fun onRollback(callback: () -> Unit) {
        boundary.filter { !it.success }.subscribe { callback() }
    }

    @Synchronized
    fun onClose(callback: () -> Unit) {
        if (closed) callback() else boundary.subscribe { callback() }
    }
}

/**
 * Wrapper exception, for any exception registered as [DatabaseTransaction.firstExceptionInDatabaseTransaction].
 */
class DatabaseTransactionException(override val cause: Throwable): CordaRuntimeException(cause.message, cause)

class RolledBackDatabaseSessionException : CordaRuntimeException("Attempted to commit database transaction marked for rollback")