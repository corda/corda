package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.nodeapi.internal.persistence.*

interface StateMachinePersistence {
    fun executeCreateTransaction()
    fun executeRollbackTransaction()
    fun executeCommitTransaction()
    fun removeContextTransaction(): DatabaseTransaction
    fun checkContextTransaction(isPresent: Boolean)
    fun setContextTransaction(transaction: DatabaseTransaction)
    fun setContextDatabase(database: CordaPersistence)
}

// Deliberately not an object to avoid unintentional imports of these methods.
class NodeStateMachinePersistence : StateMachinePersistence {
    @Suspendable
    override fun executeCreateTransaction() {
        if (contextTransactionOrNull != null) {
            throw IllegalStateException("Refusing to create a second transaction")
        }
        contextDatabase.newTransaction()
    }

    @Suspendable
    override fun executeRollbackTransaction() {
        contextTransactionOrNull?.close()
    }

    @Suspendable
    override fun executeCommitTransaction() {
        try {
            contextTransaction.commit()
        } finally {
            contextTransaction.close()
            contextTransactionOrNull = null
        }
    }

    @Suspendable
    override fun removeContextTransaction(): DatabaseTransaction {
        val transaction = contextTransaction
        contextTransactionOrNull = null
        return transaction
    }

    @Suspendable
    override fun checkContextTransaction(isPresent: Boolean) {
        if (isPresent) {
            requireNotNull(contextTransactionOrNull)
        } else {
            require(contextTransactionOrNull == null)
        }
    }

    @Suspendable
    override fun setContextTransaction(transaction: DatabaseTransaction) {
        contextTransactionOrNull = transaction
    }

    @Suspendable
    override fun setContextDatabase(database: CordaPersistence) {
        contextDatabase = database
    }
}
