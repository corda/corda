package net.corda.node.services.persistence

import com.google.common.annotations.VisibleForTesting
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.DataFeed
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.*
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.utilities.*
import rx.Observable
import rx.subjects.PublishSubject
import javax.persistence.*

class DBTransactionStorage : WritableTransactionStorage, SingletonSerializeAsToken() {

    object TransactionSchema

    object TransactionSchemaV1 : MappedSchema(schemaFamily = TransactionSchema.javaClass, version = 1,
            mappedTypes = listOf(Transaction::class.java)) {

        @Entity
        @Table(name = "${NODE_DATABASE_PREFIX}transactions")
        class Transaction(
                @Id
                @Column(name = "tx_id", length = 64)
                var txId: String = "",

                @Lob
                @Column
                var transaction: ByteArray = ByteArray(0)
        )
    }

    private companion object {
        fun createTransactionsMap(): AppendOnlyPersistentMap<SecureHash, SignedTransaction, TransactionSchemaV1.Transaction, String> {
            return AppendOnlyPersistentMap(
                    cacheBound = 1024,
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = { Pair(SecureHash.parse(it.txId),
                            deserializeFromByteArray<SignedTransaction>(it.transaction, context = SerializationDefaults.STORAGE_CONTEXT)) },
                    toPersistentEntity = { key: SecureHash, value: SignedTransaction ->
                        TransactionSchemaV1.Transaction().apply {
                            txId = key.toString()
                            transaction = serializeToByteArray(value, context = SerializationDefaults.STORAGE_CONTEXT)
                        }
                    },
                    persistentEntityClass = TransactionSchemaV1.Transaction::class.java
            )
        }
    }

    private val txStorage = createTransactionsMap()

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        txStorage[transaction.id] = transaction
        updatesPublisher.bufferUntilDatabaseCommit().onNext(transaction)
        return true
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = txStorage[id]

    private val updatesPublisher = PublishSubject.create<SignedTransaction>().toSerialized()
    override val updates: Observable<SignedTransaction> = updatesPublisher.wrapWithDatabaseTransaction()

    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> =
            DataFeed(txStorage.loadAll().map { it.second }.toList(), updatesPublisher.bufferUntilSubscribed().wrapWithDatabaseTransaction())

    @VisibleForTesting
    val transactions: Iterable<SignedTransaction> get() = txStorage.loadAll().map { it.second }.toList()
}
