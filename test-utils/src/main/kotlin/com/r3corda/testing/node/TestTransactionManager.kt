package com.r3corda.testing.node

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

/**
 * A dummy transaction manager used by [MockNode] to avoid uninitialised lateinit var.  Any attempt to use this results in an exception.
 */
class TestTransactionManager : TransactionManager {

    var current = ThreadLocal<Transaction>()

    override fun currentOrNull() = current.get()

    override fun newTransaction(isolation: Int): Transaction {
        val newTx = Transaction(TestTransactionImpl(this))
        current.set(newTx)
        return newTx
    }

    class TestTransactionImpl(val manager: TestTransactionManager) : TransactionInterface {
        override val connection: Connection
            get() = throw UnsupportedOperationException()
        override val db: Database
            get() = throw UnsupportedOperationException()
        override val outerTransaction: Transaction?
            get() = throw UnsupportedOperationException()

        override fun close() {
            manager.current.set(null)
        }

        override fun commit() {
        }

        override fun rollback() {
        }

    }
}