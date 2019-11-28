package net.corda.nodeapi.internal.persistence

interface CordaTransactionSupport {
    /**
     * Executes given statement in the scope of transaction with the transaction level specified at the creation time.
     * @param statement to be executed in the scope of this transaction.
     */
    fun <T> transaction(statement: DatabaseTransaction.() -> T): T
}