package net.corda.node.services.persistence

import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.*
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.utilities.*
import rx.Observable
import rx.subjects.PublishSubject
import javax.persistence.*

class DBTransactionStorage : WritableTransactionStorage, SingletonSerializeAsToken() {

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}transactions")
    class DBTransaction(
            @Id
            @Column(name = "tx_id", length = 64)
            var txId: String = "",

            @Lob
            @Column
            var transaction: ByteArray = ByteArray(0)
    )

    private companion object {
        fun createTransactionsMap(): AppendOnlyPersistentMap<SecureHash, SignedTransaction, DBTransaction, String> {
            return AppendOnlyPersistentMap(
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = {
                        Pair(SecureHash.parse(it.txId),
                                it.transaction.deserialize<SignedTransaction>(context = SerializationDefaults.STORAGE_CONTEXT))
                    },
                    toPersistentEntity = { key: SecureHash, value: SignedTransaction ->
                        DBTransaction().apply {
                            txId = key.toString()
                            transaction = value.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                        }
                    },
                    persistentEntityClass = DBTransaction::class.java
            )
        }
    }

    private val txStorage = createTransactionsMap()

    override fun addTransaction(transaction: SignedTransaction): Boolean =
            txStorage.addWithDuplicatesAllowed(transaction.id, transaction).apply {
                updatesPublisher.bufferUntilDatabaseCommit().onNext(transaction)
            }

    override fun getTransaction(id: SecureHash): SignedTransaction? = txStorage[id]

    private val updatesPublisher = PublishSubject.create<SignedTransaction>().toSerialized()
    override val updates: Observable<SignedTransaction> = updatesPublisher.wrapWithDatabaseTransaction()

    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> =
            DataFeed(txStorage.allPersisted().map { it.second }.toList(), updatesPublisher.bufferUntilSubscribed().wrapWithDatabaseTransaction())

    @VisibleForTesting
    val transactions: Iterable<SignedTransaction>
        get() = txStorage.allPersisted().map { it.second }.toList()
}
