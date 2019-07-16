package net.corda.core.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.TransactionStorage
import net.corda.core.transactions.SignedTransaction

/**
 * Thread-safe storage of transactions.
 */
interface WritableTransactionStorage : TransactionStorage {
    /**
     * Add a new *verified* transaction to the store, or convert the existing unverified transaction into a verified one.
     * @param transaction The transaction to be recorded.
     * @return true if the transaction was recorded as a *new verified* transcation, false if the transaction already exists.
     */
    // TODO: Throw an exception if trying to add a transaction with fewer signatures than an existing entry.
    fun addTransaction(transaction: SignedTransaction): Boolean

    /**
     * Add a new *unverified* transaction to the store.
     */
    fun addUnverifiedTransaction(transaction: SignedTransaction)

    /**
     * Return the transaction with the given ID from the store, and a flag of whether it's verified. Returns null if no transaction with the
     * ID exists.
     */
    fun getTransactionInternal(id: SecureHash): Pair<SignedTransaction, Boolean>?
}