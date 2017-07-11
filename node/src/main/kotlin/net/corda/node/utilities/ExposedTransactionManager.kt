package net.corda.node.utilities

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

/**
 * Wrapper of [DatabaseTransaction], because the class is effectively used for [ExposedTransaction.connection] method only not all methods are implemented.
 * The class will obsolete when Exposed library is phased out.
 */
class ExposedTransaction(override val db: Database, val databaseTransaction: DatabaseTransaction) : TransactionInterface {

    override val outerTransaction: Transaction?
        get() = TODO("not implemented")

    override val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
        databaseTransaction.connection
    }

    override fun commit() {
        databaseTransaction.commit()
    }

    override fun rollback() {
        databaseTransaction.rollback()
    }

    override fun close() {
        databaseTransaction.close()
    }
}

/**
 * Delegates methods to [DatabaseTransactionManager].
 * The class will obsolete when Exposed library is phased out.
 */
class ExposedTransactionManager: TransactionManager {

    companion object {
       val database: Database
            get() =  DatabaseTransactionManager.dataSource.database
    }

    override fun newTransaction(isolation: Int): Transaction {
        var cordaTransaction = DatabaseTransactionManager.newTransaction(isolation)
        return Transaction(ExposedTransaction(database, cordaTransaction))
    }

    override fun currentOrNull(): Transaction? {
        val cordaTransaction = DatabaseTransactionManager.currentOrNull()
        return if (cordaTransaction != null) Transaction(ExposedTransaction(database, cordaTransaction)) else null
    }
}