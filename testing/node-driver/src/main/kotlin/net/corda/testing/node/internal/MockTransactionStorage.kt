package net.corda.testing.node.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.toFuture
import net.corda.core.transactions.EncryptedTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.testing.node.MockServices
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

/**
 * A class which provides an implementation of [WritableTransactionStorage] which is used in [MockServices]
 */
open class MockTransactionStorage : WritableTransactionStorage, SingletonSerializeAsToken() {
    override fun trackTransaction(id: SecureHash): CordaFuture<SignedTransaction> {
        return getTransaction(id)?.let { doneFuture(it) } ?: _updatesPublisher.filter { it.id == id }.toFuture()
    }

    override fun trackTransactionWithNoWarning(id: SecureHash): CordaFuture<SignedTransaction> {
        return trackTransaction(id)
    }

    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return DataFeed(txns.values.mapNotNull { if (it.isVerified) it.stx else null }, _updatesPublisher)
    }

    private val txns = HashMap<SecureHash, TxHolder>()
    private val encryptedTxns = HashMap<SecureHash, EncryptedTxHolder>()

    private val _updatesPublisher = PublishSubject.create<SignedTransaction>()

    override val updates: Observable<SignedTransaction>
        get() = _updatesPublisher

    private fun notify(transaction: SignedTransaction): Boolean {
        _updatesPublisher.onNext(transaction)
        return true
    }

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        val current = txns.putIfAbsent(transaction.id, TxHolder(transaction, isVerified = true))
        return if (current == null) {
            notify(transaction)
        } else if (!current.isVerified) {
            current.isVerified = true
            notify(transaction)
        } else {
            false
        }
    }

    override fun addUnverifiedTransaction(transaction: SignedTransaction) {
        txns.putIfAbsent(transaction.id, TxHolder(transaction, isVerified = false))
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = txns[id]?.let { if (it.isVerified) it.stx else null }

    override fun getTransactionInternal(id: SecureHash): Pair<SignedTransaction, Boolean>? = txns[id]?.let { Pair(it.stx, it.isVerified) }

    override fun addVerifiedEncryptedTransaction(encryptedTransaction: EncryptedTransaction): Boolean {
        val current = encryptedTxns.putIfAbsent(encryptedTransaction.id, EncryptedTxHolder(encryptedTransaction, isVerified = true))
        return if (current == null) {
            true
        } else if (!current.isVerified) {
            current.isVerified = true
            true
        } else {
            false
        }
    }

    override fun addUnverifiedEncryptedTransaction(encryptedTransaction: EncryptedTransaction) {
        encryptedTxns.putIfAbsent(encryptedTransaction.id, EncryptedTxHolder(encryptedTransaction, isVerified = false))
    }

    override fun getEncryptedTransaction(id: SecureHash): EncryptedTransaction? = encryptedTxns[id]?.let { if (it.isVerified) it.et else null }

    override fun getEncryptedTransactionInternal(id: SecureHash): Pair<EncryptedTransaction, Boolean>? =
            encryptedTxns[id]?.let { Pair(it.et, it.isVerified) }

    private class TxHolder(val stx: SignedTransaction, var isVerified: Boolean)
    private class EncryptedTxHolder(val et: EncryptedTransaction, var isVerified: Boolean)
}