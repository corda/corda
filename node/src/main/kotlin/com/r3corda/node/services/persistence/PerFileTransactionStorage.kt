package com.r3corda.node.services.persistence

import com.r3corda.core.ThreadBox
import com.r3corda.core.bufferUntilSubscribed
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.TransactionStorage
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import rx.Observable
import rx.subjects.PublishSubject
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
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

    private val mutex = ThreadBox(object {
        val transactionsMap = HashMap<SecureHash, SignedTransaction>()
        val updatesPublisher = PublishSubject.create<SignedTransaction>()

        fun notify(transaction: SignedTransaction) = updatesPublisher.onNext(transaction)
    })

    override val updates: Observable<SignedTransaction>
        get() = mutex.content.updatesPublisher

    init {
        logger.trace { "Initialising per file transaction storage on $storeDir" }
        Files.createDirectories(storeDir)
        mutex.locked {
            Files.list(storeDir)
                    .filter { it.toString().toLowerCase().endsWith(fileExtension) }
                    .map { Files.readAllBytes(it).deserialize<SignedTransaction>() }
                    .forEach { transactionsMap[it.id] = it }
        }
    }

    override fun addTransaction(transaction: SignedTransaction) {
        val transactionFile = storeDir.resolve("${transaction.id.toString().toLowerCase()}$fileExtension")
        transaction.serialize().writeToFile(transactionFile)
        mutex.locked {
            transactionsMap[transaction.id] = transaction
            notify(transaction)
        }
        logger.trace { "Stored $transaction to $transactionFile" }
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = mutex.locked { transactionsMap[id] }

    val transactions: Iterable<SignedTransaction> get() = mutex.locked { transactionsMap.values.toList() }

    override fun track(): Pair<List<SignedTransaction>, Observable<SignedTransaction>> {
        return mutex.locked {
            Pair(transactionsMap.values.toList(), updates.bufferUntilSubscribed())
        }
    }

}
