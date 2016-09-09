package com.r3corda.node.services.persistence

import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.TransactionStorage
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import rx.Observable
import rx.subjects.PublishSubject
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe

/**
 * File-based transaction storage, storing transactions per file.
 */
@ThreadSafe
class PerFileTransactionStorage(val storeDir: Path) : TransactionStorage {

    companion object {
        private val logger = loggerFor<PerFileCheckpointStorage>()
        private val fileExtension = ".transaction"
    }

    private val _transactions = ConcurrentHashMap<SecureHash, SignedTransaction>()

    private val _updatesPublisher = PublishSubject.create<SignedTransaction>()

    override val updates: Observable<SignedTransaction>
        get() = _updatesPublisher

    private fun notify(transaction: SignedTransaction) = _updatesPublisher.onNext(transaction)

    init {
        logger.trace { "Initialising per file transaction storage on $storeDir" }
        Files.createDirectories(storeDir)
        Files.list(storeDir)
                .filter { it.toString().toLowerCase().endsWith(fileExtension) }
                .map { Files.readAllBytes(it).deserialize<SignedTransaction>() }
                .forEach { _transactions[it.id] = it }
    }

    override fun addTransaction(transaction: SignedTransaction) {
        val transactionFile = storeDir.resolve("${transaction.id.toString().toLowerCase()}$fileExtension")
        transaction.serialize().writeToFile(transactionFile)
        _transactions[transaction.id] = transaction
        logger.trace { "Stored $transaction to $transactionFile" }
        notify(transaction)
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = _transactions[id]

    val transactions: Iterable<SignedTransaction> get() = _transactions.values.toList()

}