package net.corda.nodeapi.internal.persistence

import org.hibernate.Session
import org.hibernate.Transaction
import rx.subjects.Subject
import java.sql.Connection
import java.util.*

class DatabaseTransaction(
        isolation: Int,
        private val threadLocal: ThreadLocal<DatabaseTransaction>,
        private val transactionBoundaries: Subject<DatabaseTransactionManager.Boundary, DatabaseTransactionManager.Boundary>,
        val cordaPersistence: CordaPersistence
) {
    val id: UUID = UUID.randomUUID()

    private var _connectionCreated = false
    val connectionCreated get() = _connectionCreated
    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        cordaPersistence.dataSource.connection
                .apply {
                    _connectionCreated = true
                    // only set the transaction isolation level if it's actually changed - setting isn't free.
                    if (transactionIsolation != isolation) {
                        transactionIsolation = isolation
                    }
                }
    }

    private val sessionDelegate = lazy {
        val session = cordaPersistence.entityManagerFactory.withOptions().connection(connection).openSession()
        hibernateTransaction = session.beginTransaction()
        session
    }

    val session: Session by sessionDelegate
    private lateinit var hibernateTransaction: Transaction

    val outerTransaction: DatabaseTransaction? = threadLocal.get()

    fun commit() {
        if (sessionDelegate.isInitialized()) {
            hibernateTransaction.commit()
        }
        if (_connectionCreated) {
            connection.commit()
        }
    }

    fun rollback() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.clear()
        }
        if (_connectionCreated && !connection.isClosed) {
            connection.rollback()
        }
    }

    fun close() {
        if (sessionDelegate.isInitialized() && session.isOpen) {
            session.close()
        }
        if (_connectionCreated) {
            connection.close()
        }
        threadLocal.set(outerTransaction)
        if (outerTransaction == null) {
            transactionBoundaries.onNext(DatabaseTransactionManager.Boundary(id))
        }
    }
}
