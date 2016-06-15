package com.r3corda.core.node.services

import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.crypto.SecureHash

/**
 * Thread-safe storage of transactions.
 */
interface ReadOnlyTransactionStorage {
    /**
     * Return the transaction with the given [id], or null if no such transaction exists.
     */
    fun getTransaction(id: SecureHash): SignedTransaction?
}

/**
 * Thread-safe storage of transactions.
 */
interface TransactionStorage : ReadOnlyTransactionStorage {
    /**
     * Add a new transaction to the store. If the store already has a transaction with the same id it will be
     * overwritten.
     */
    // TODO: Throw an exception if trying to add a transaction with fewer signatures than an existing entry.
    fun addTransaction(transaction: SignedTransaction)
}
