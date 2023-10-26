package net.corda.testing.node.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.core.flows.TransactionMetadata
import net.corda.core.flows.TransactionStatus
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.SignedTransactionWithStatus
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

    private val _updatesPublisher = PublishSubject.create<SignedTransaction>()

    override val updates: Observable<SignedTransaction>
        get() = _updatesPublisher

    private fun notify(transaction: SignedTransaction): Boolean {
        _updatesPublisher.onNext(transaction)
        return true
    }

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        val current = txns.putIfAbsent(transaction.id, TxHolder(transaction, status = TransactionStatus.VERIFIED))
        return if (current == null) {
            notify(transaction)
        } else if (!current.isVerified) {
            notify(transaction)
        } else {
            false
        }
    }

    override fun addUnnotarisedTransaction(transaction: SignedTransaction): Boolean {
        return txns.putIfAbsent(transaction.id, TxHolder(transaction, status = TransactionStatus.IN_FLIGHT)) == null
    }

    override fun addSenderTransactionRecoveryMetadata(txId: SecureHash, metadata: TransactionMetadata): ByteArray? { return null }

    override fun addReceiverTransactionRecoveryMetadata(txId: SecureHash,
                                                        sender: CordaX500Name,
                                                        metadata: TransactionMetadata) { }

    override fun removeUnnotarisedTransaction(id: SecureHash): Boolean {
        return txns.remove(id) != null
    }

    override fun finalizeTransaction(transaction: SignedTransaction) =
            addTransaction(transaction)

    override fun finalizeTransactionWithExtraSignatures(transaction: SignedTransaction, signatures: Collection<TransactionSignature>): Boolean {
        val current = txns.replace(transaction.id, TxHolder(transaction, status = TransactionStatus.VERIFIED))
        return if (current != null) {
            notify(transaction)
        } else {
            false
        }
    }

    override fun addUnverifiedTransaction(transaction: SignedTransaction) {
        txns.putIfAbsent(transaction.id, TxHolder(transaction, status = TransactionStatus.UNVERIFIED))
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = txns[id]?.let { if (it.isVerified) it.stx else null }

    override fun getTransactionWithStatus(id: SecureHash): SignedTransactionWithStatus? = txns[id]?.let { SignedTransactionWithStatus(it.stx, it.status) }

    private class TxHolder(val stx: SignedTransaction, var status: TransactionStatus) {
        val isVerified = status == TransactionStatus.VERIFIED
    }
}