package net.corda.nodeapi.internal.persistence

import co.paralleluniverse.strands.Strand
import net.corda.core.CordaRuntimeException
import org.hibernate.Session
import org.hibernate.Transaction
import rx.subjects.PublishSubject
import java.sql.Connection
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
        val outerTransaction: DatabaseTransaction?,
        val database: CordaPersistence
) {
    val id: UUID = UUID.randomUUID()

    private var _connectionCreated = false
    val connectionCreated get() = _connectionCreated
    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        database.dataSource.connection.apply {
            _connectionCreated = true
            // only set the transaction isolation level if it's actually changed - setting isn't free.
            if (transactionIsolation != isolation) {
                transactionIsolation = isolation
            }
        }
    }

    private val sessionDelegate = lazy {
        val session = database.entityManagerFactory.withOptions().connection(connection).openSession()
        hibernateTransaction = session.beginTransaction()
        session
    }

    // Returns a delegate which overrides certain operations that we do not want CorDapp developers to call.
    val restrictedEntityManager: RestrictedEntityManager by lazy {
        val entityManager = session as EntityManager
        RestrictedEntityManager(entityManager)
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

    fun commit() {
        firstExceptionInDatabaseTransaction?.let {
            throw DatabaseTransactionException(it)
        }
        if (sessionDelegate.isInitialized()) {
            hibernateTransaction.commit()
        }
        if (_connectionCreated) {
            connection.commit()
        }
        committed = true
    }

    fun rollback() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.clear()
        }
        if (_connectionCreated && !connection.isClosed) {
            connection.rollback()
        }
        clearException()
    }

    fun close() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.close()
        }

        if (_connectionCreated && database.closeConnection) {
            connection.close()
        }
        clearException()

        contextTransactionOrNull = outerTransaction
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