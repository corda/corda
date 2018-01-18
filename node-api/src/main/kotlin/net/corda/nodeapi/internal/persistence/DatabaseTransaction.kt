package net.corda.nodeapi.internal.persistence

import co.paralleluniverse.strands.Strand
import org.hibernate.Session
import org.hibernate.Transaction
import java.sql.Connection
import java.util.*

fun currentDBSession(): Session = contextTransaction.session
private val _contextTransaction = ThreadLocal<DatabaseTransaction>()
var contextTransactionOrNull: DatabaseTransaction?
    get() = _contextTransaction.get()
    set(transaction) = _contextTransaction.set(transaction)
val contextTransaction get() = contextTransactionOrNull ?: error("Was expecting to find transaction set on current strand: ${Strand.currentStrand()}")

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

    val session: Session by sessionDelegate
    private lateinit var hibernateTransaction: Transaction
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
        contextTransactionOrNull = outerTransaction
        if (outerTransaction == null) {
            database.transactionBoundaries.onNext(CordaPersistence.Boundary(id))
        }
    }
}
