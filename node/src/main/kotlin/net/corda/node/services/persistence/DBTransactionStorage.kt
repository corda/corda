package net.corda.node.services.persistence

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.toFuture
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.node.utilities.WeightBasedAppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.*
import rx.Observable
import rx.subjects.PublishSubject
import javax.persistence.*
import kotlin.streams.toList

class DBTransactionStorage(private val database: CordaPersistence, cacheFactory: NamedCacheFactory) : WritableTransactionStorage, SingletonSerializeAsToken() {

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}transactions")
    class DBTransaction(
            @Id
            @Column(name = "tx_id", length = 64, nullable = false)
            val txId: String,

            @Column(name = "state_machine_run_id", length = 36, nullable = true)
            val stateMachineRunId: String?,

            @Lob
            @Column(name = "transaction_value", nullable = false)
            val transaction: ByteArray,

            @Column(name = "is_verified", nullable = false)
            val isVerified: Boolean
    )

    private companion object {
        // Rough estimate for the average of a public key and the transaction metadata - hard to get exact figures here,
        // as public keys can vary in size a lot, and if someone else is holding a reference to the key, it won't add
        // to the memory pressure at all here.
        private const val transactionSignatureOverheadEstimate = 1024

        private val logger = contextLogger()

        private fun contextToUse(): SerializationContext {
            return if (effectiveSerializationEnv.serializationFactory.currentContext?.useCase == SerializationContext.UseCase.Storage) {
                effectiveSerializationEnv.serializationFactory.currentContext!!
            } else {
                SerializationDefaults.STORAGE_CONTEXT
            }
        }

        fun createTransactionsMap(cacheFactory: NamedCacheFactory)
                : AppendOnlyPersistentMapBase<SecureHash, TxCacheValue, DBTransaction, String> {
            return WeightBasedAppendOnlyPersistentMap<SecureHash, TxCacheValue, DBTransaction, String>(
                    cacheFactory = cacheFactory,
                    name = "DBTransactionStorage_transactions",
                    toPersistentEntityKey = SecureHash::toString,
                    fromPersistentEntity = {
                        SecureHash.parse(it.txId) to TxCacheValue(it.transaction.deserialize(context = contextToUse()), it.isVerified)
                    },
                    toPersistentEntity = { key: SecureHash, value: TxCacheValue ->
                        DBTransaction(
                                txId = key.toString(),
                                stateMachineRunId = FlowStateMachineImpl.currentStateMachine()?.id?.uuid?.toString(),
                                transaction = value.toSignedTx().serialize(context = contextToUse()).bytes,
                                isVerified = value.isVerified
                        )
                    },
                    persistentEntityClass = DBTransaction::class.java,
                    weighingFunc = { hash, tx -> hash.size + weighTx(tx) }
            )
        }

        private fun weighTx(tx: AppendOnlyPersistentMapBase.Transactional<TxCacheValue>): Int {
            val actTx = tx.peekableValue ?: return 0
            return actTx.sigs.sumBy { it.size + transactionSignatureOverheadEstimate } + actTx.txBits.size
        }
    }

    private val txStorage = ThreadBox(createTransactionsMap(cacheFactory))

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        return database.transaction {
            txStorage.locked {
                val added = addWithDuplicatesAllowed(transaction.id, TxCacheValue(transaction, isVerified = true))
                if (added) {
                    logger.debug { "Recorded transaction ${transaction.id}." }
                    onNewTx(transaction)
                } else {
                    // We need to check that what exists in the database is verified or not.
                    if (get(transaction.id)!!.isVerified) {
                        logger.debug { "Transaction ${transaction.id} already exists so no need to record." }
                        // Transaction is already verified so there's nothing to do
                        false
                    } else {
                        // If it isn't verified then we can simply flip the switch and then report the transaction as "added" as per the
                        // contract for this method.
                        invalidate(transaction.id)
                        currentDBSession()
                                .createQuery("UPDATE ${DBTransaction::class.java.name} T SET T.isVerified = true WHERE T.txId = :txId")
                                .setParameter("txId", transaction.id.toString())
                                .executeUpdate()
                        logger.debug { "Previously unverified transaction ${transaction.id} has been recorded as verified." }
                        onNewTx(transaction)
                    }
                }
            }
        }
    }

    private fun onNewTx(transaction: SignedTransaction): Boolean {
        updatesPublisher.bufferUntilDatabaseCommit().onNext(transaction)
        return true
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? {
        return database.transaction {
            txStorage.content[id]?.let { if (it.isVerified) it.toSignedTx() else null }
        }
    }

    override fun addUnverifiedTransaction(transaction: SignedTransaction) {
        database.transaction {
            txStorage.locked {
                val added = addWithDuplicatesAllowed(transaction.id, TxCacheValue(transaction, isVerified = false))
                if (added) {
                    logger.debug { "Transaction ${transaction.id} recorded as unverified." }
                } else {
                    logger.info("Transaction ${transaction.id} already exists so no need to record.")
                }
            }
        }
    }

    override fun getTransactionInternal(id: SecureHash): Pair<SignedTransaction, Boolean>? {
        return database.transaction {
            txStorage.content[id]?.let { it.toSignedTx() to it.isVerified }
        }
    }

    private val updatesPublisher = PublishSubject.create<SignedTransaction>().toSerialized()
    override val updates: Observable<SignedTransaction> = updatesPublisher.wrapWithDatabaseTransaction()

    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return database.transaction {
            txStorage.locked {
                DataFeed(snapshot(), updates.bufferUntilSubscribed())
            }
        }
    }

    override fun trackTransaction(id: SecureHash): CordaFuture<SignedTransaction> {
        return database.transaction {
            txStorage.locked {
                val existingTransaction = getTransaction(id)
                if (existingTransaction == null) {
                    updates.filter { it.id == id }.toFuture()
                } else {
                    doneFuture(existingTransaction)
                }
            }
        }
    }

    // Cache value type to just store the immutable bits of a signed transaction plus conversion helpers
    private data class TxCacheValue(
            val txBits: SerializedBytes<CoreTransaction>,
            val sigs: List<TransactionSignature>,
            val isVerified: Boolean
    ) {
        constructor(stx: SignedTransaction, isVerified: Boolean) : this(stx.txBits, stx.sigs, isVerified)
        fun toSignedTx() = SignedTransaction(txBits, sigs)
    }

    @VisibleForTesting
    val transactions: List<SignedTransaction> get() = database.transaction { snapshot() }

    private fun snapshot(): List<SignedTransaction> {
        return txStorage.content.allPersisted.use {
            it.filter { it.second.isVerified }.map { it.second.toSignedTx() }.toList()
        }
    }
}
