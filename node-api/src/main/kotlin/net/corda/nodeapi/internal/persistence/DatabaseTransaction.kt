package net.corda.nodeapi.internal.persistence

import co.paralleluniverse.strands.Strand
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
    val restrictedEntityManager: RestrictedEntityManager by lazy {
        val entityManager = session as EntityManager
        RestrictedEntityManager(entityManager)
    }

    val session: Session by sessionDelegate
    private lateinit var hibernateTransaction: Transaction

    internal val boundary = PublishSubject.create<CordaPersistence.Boundary>()
    private var committed = false
    private var closed = false

    // Holds the exception that broke the atomicity of a DatabaseTransaction statement or
    // holds the very first exception that broke the atomicity of the innermost DatabaseTransaction statement
    // in a chain of nested CordaPersistence#transaction calls. The exception will be rethrown on the next DatabaseTransaction#commit.
    // The purpose of this property is to make sure these exceptions cannot be suspended in user code.
    // This property will get written only inside a flow state machine execution.
    var databaseTxStatementBrokenBy: Throwable? = null

    fun commit() {
        databaseTxStatementBrokenBy?.let {
            throw it
        }
        if (sessionDelegate.isInitialized()) {
            hibernateTransaction.commit()
        }
        connection.commit()
        committed = true
    }

    fun rollback() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.clear()
        }
        if (!connection.isClosed) {
            connection.rollback()
        }
    }

    fun close() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.close()
            databaseTxStatementBrokenBy = null
        }

        if (database.closeConnection) {
            connection.close()
        }
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

