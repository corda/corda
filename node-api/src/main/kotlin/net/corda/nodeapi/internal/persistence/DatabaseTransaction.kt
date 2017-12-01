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

    val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        cordaPersistence.dataSource.connection.apply {
            autoCommit = false
            transactionIsolation = isolation
        }
    }

    private val sessionDelegate = lazy {
        val session = cordaPersistence.entityManagerFactory.withOptions().connection(connection).openSession()
        hibernateTransaction = session.beginTransaction()
        session
    }

    val session: Session by sessionDelegate
    private lateinit var hibernateTransaction: Transaction

    private val outerTransaction: DatabaseTransaction? = threadLocal.get()

    fun commit() {
        if (sessionDelegate.isInitialized()) {
            hibernateTransaction.commit()
        }
        connection.commit()
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
        }
        connection.close()
        threadLocal.set(outerTransaction)
        if (outerTransaction == null) {
            transactionBoundaries.onNext(DatabaseTransactionManager.Boundary(id))
        }
    }
}
