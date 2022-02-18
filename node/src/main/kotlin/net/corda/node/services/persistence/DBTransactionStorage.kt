package net.corda.node.services.persistence

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.serialization.*
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.toFuture
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.EncryptedTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.CordaClock
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.node.utilities.WeightBasedAppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.*
import net.corda.serialization.internal.CordaSerializationEncoding.SNAPPY
import rx.Observable
import rx.subjects.PublishSubject
import java.time.Instant
import java.util.*
import javax.persistence.*
import kotlin.streams.toList

class DBTransactionStorage(private val database: CordaPersistence, cacheFactory: NamedCacheFactory,
                           private val clock: CordaClock) : WritableTransactionStorage, SingletonSerializeAsToken() {

    @Suppress("MagicNumber") // database column width
    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}transactions")
    class DBTransaction(
            @Id
            @Column(name = "tx_id", length = 144, nullable = false)
            val txId: String,

            @Column(name = "state_machine_run_id", length = 36, nullable = true)
            val stateMachineRunId: String?,

            @Lob
            @Column(name = "transaction_value", nullable = false)
            val transaction: ByteArray,

            @Column(name = "status", nullable = false, length = 1)
            @Convert(converter = TransactionStatusConverter::class)
            val status: TransactionStatus,

            @Column(name = "timestamp", nullable = false)
            val timestamp: Instant,

            @Column(name = "encrypted", nullable = false)
            val encrypted: Boolean = false

            // TODO: will need to also store the signature of who verified this tx
    )

    enum class TransactionStatus {
        UNVERIFIED,
        VERIFIED;

        fun toDatabaseValue(): String {
            return when (this) {
                UNVERIFIED -> "U"
                VERIFIED -> "V"
            }
        }

        fun isVerified(): Boolean {
            return this == VERIFIED
        }

        companion object {
            fun fromDatabaseValue(databaseValue: String): TransactionStatus {
                return when (databaseValue) {
                    "V" -> VERIFIED
                    "U" -> UNVERIFIED
                    else -> throw UnexpectedStatusValueException(databaseValue)
                }
            }
        }

        private class UnexpectedStatusValueException(status: String) : Exception("Found unexpected status value $status in transaction store")
    }

    @Converter
    class TransactionStatusConverter : AttributeConverter<TransactionStatus, String> {
        override fun convertToDatabaseColumn(attribute: TransactionStatus): String {
            return attribute.toDatabaseValue()
        }

        override fun convertToEntityAttribute(dbData: String): TransactionStatus {
            return TransactionStatus.fromDatabaseValue(dbData)
        }
    }

    internal companion object {
        const val TRANSACTION_ALREADY_IN_PROGRESS_WARNING = "trackTransaction is called with an already existing, open DB transaction. As a result, there might be transactions missing from the returned data feed, because of race conditions."

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

        private fun createTransactionsMap(cacheFactory: NamedCacheFactory, clock: CordaClock)
                : AppendOnlyPersistentMapBase<SecureHash, TxCacheValue, DBTransaction, String> {
            return WeightBasedAppendOnlyPersistentMap<SecureHash, TxCacheValue, DBTransaction, String>(
                    cacheFactory = cacheFactory,
                    name = "DBTransactionStorage_transactions",
                    toPersistentEntityKey = SecureHash::toString,
                    fromPersistentEntity = {
                        if (it.encrypted) {
                            SecureHash.create(it.txId) to TxCacheValue(
                                    EncryptedTransaction(
                                            SecureHash.parse(it.txId),
                                            it.transaction
                                    ),
                                    it.status
                            )
                        } else {
                            SecureHash.create(it.txId) to TxCacheValue(
                                    it.transaction.deserialize<SignedTransaction>(context = contextToUse()),
                                    it.status
                            )
                        }
                    },
                    toPersistentEntity = { key: SecureHash, value: TxCacheValue ->
                        DBTransaction(
                                txId = key.toString(),
                                stateMachineRunId = FlowStateMachineImpl.currentStateMachine()?.id?.uuid?.toString(),
                                transaction = if( value.encrypted ) {
                                    value.txBits
                                } else {
                                    value.toSignedTx().serialize(context = contextToUse().withEncoding(SNAPPY)).bytes
                                },
                                status = value.status,
                                timestamp = clock.instant(),
                                encrypted = value.encrypted
                        )
                    },
                    persistentEntityClass = DBTransaction::class.java,
                    weighingFunc = { hash, tx -> hash.size + weighTx(tx) }
            )
        }

        // TODO: weight of transactions will be wrong at this stage for encrypted transactions
        private fun weighTx(tx: AppendOnlyPersistentMapBase.Transactional<TxCacheValue>): Int {
            val actTx = tx.peekableValue ?: return 0
            return actTx.sigs.sumBy { it.size + transactionSignatureOverheadEstimate } + actTx.txBits.size
        }

        private val log = contextLogger()
    }

    private val txStorage = ThreadBox(createTransactionsMap(cacheFactory, clock))

    private fun updateTransaction(txId: SecureHash): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(DBTransaction::class.java)
        val updateRoot = criteriaUpdate.from(DBTransaction::class.java)
        criteriaUpdate.set(updateRoot.get<TransactionStatus>(DBTransaction::status.name), TransactionStatus.VERIFIED)
        criteriaUpdate.where(criteriaBuilder.and(
                criteriaBuilder.equal(updateRoot.get<String>(DBTransaction::txId.name), txId.toString()),
                criteriaBuilder.equal(updateRoot.get<TransactionStatus>(DBTransaction::status.name), TransactionStatus.UNVERIFIED)
        ))
        criteriaUpdate.set(updateRoot.get<Instant>(DBTransaction::timestamp.name), clock.instant())
        val update = session.createQuery(criteriaUpdate)
        val rowsUpdated = update.executeUpdate()
        return rowsUpdated != 0
    }

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        return database.transaction {
            txStorage.locked {
                val cachedValue = TxCacheValue(transaction, TransactionStatus.VERIFIED)
                val addedOrUpdated = addOrUpdate(transaction.id, cachedValue) { k, _ -> updateTransaction(k) }
                if (addedOrUpdated) {
                    logger.debug { "Transaction ${transaction.id} has been recorded as verified" }
                    onNewTx(transaction)
                } else {
                    logger.debug { "Transaction ${transaction.id} is already recorded as verified, so no need to re-record" }
                    false
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
            txStorage.content[id]?.let { if (it.status.isVerified() && !it.encrypted ) it.toSignedTx() else null }
        }
    }

    override fun addUnverifiedTransaction(transaction: SignedTransaction) {
        database.transaction {
            txStorage.locked {
                val cacheValue = TxCacheValue(transaction, status = TransactionStatus.UNVERIFIED)
                val added = addWithDuplicatesAllowed(transaction.id, cacheValue)
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
            txStorage.content[id]?.let {
                if (!it.encrypted) {
                    it.toSignedTx() to it.status.isVerified()
                } else null
            }
        }
    }

    override fun addEncryptedTransaction(encryptedTransaction: EncryptedTransaction): Boolean {
        val transactionId = encryptedTransaction.id
        return database.transaction {
            txStorage.locked {
                val cachedValue = TxCacheValue(encryptedTransaction, TransactionStatus.VERIFIED)
                val addedOrUpdated = addOrUpdate(transactionId, cachedValue) { k, _ -> updateTransaction(k) }
                if (addedOrUpdated) {
                    logger.debug { "Transaction $transactionId has been recorded as verified" }
                } else {
                    logger.debug { "Transaction $transactionId is already recorded as verified, so no need to re-record" }
                }
                addedOrUpdated
            }
        }
    }

    override fun getEncryptedTransaction(id: SecureHash): EncryptedTransaction? {
        return database.transaction {
            txStorage.content[id]?.let { if (it.status.isVerified() && it.encrypted ) it.toEncryptedTx() else null }
        }
    }

    override fun addUnverifiedEncryptedTransaction(encryptedTransaction: EncryptedTransaction) {
        val transactionId = encryptedTransaction.id
        database.transaction {
            txStorage.locked {
                val cacheValue = TxCacheValue(encryptedTransaction, status = TransactionStatus.UNVERIFIED)
                val added = addWithDuplicatesAllowed(transactionId, cacheValue)
                if (added) {
                    logger.debug { "Encrypted Transaction $transactionId recorded as unverified." }
                } else {
                    logger.info("Encrypted Transaction $transactionId already exists so no need to record.")
                }
            }
        }
    }

    override fun getEncryptedTransactionInternal(id: SecureHash): Pair<EncryptedTransaction, Boolean>? {
        return database.transaction {
            txStorage.content[id]?.let {
                if (it.encrypted) {
                    it.toEncryptedTx() to it.status.isVerified()
                } else null
            }
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
        val (transaction, warning) = trackTransactionInternal(id)
        warning?.also { log.warn(it) }
        return transaction
    }

    /**
     * @return a pair of the signed transaction, and a string containing any warning.
     */
    internal fun trackTransactionInternal(id: SecureHash): Pair<CordaFuture<SignedTransaction>, String?> {
        val warning: String? = if (contextTransactionOrNull != null) {
            TRANSACTION_ALREADY_IN_PROGRESS_WARNING
        } else {
            null
        }

        return Pair(trackTransactionWithNoWarning(id), warning)
    }

    override fun trackTransactionWithNoWarning(id: SecureHash): CordaFuture<SignedTransaction> {
        val updateFuture = updates.filter { it.id == id }.toFuture()
        return database.transaction {
            txStorage.locked {
                val existingTransaction = getTransaction(id)
                if (existingTransaction == null) {
                    updateFuture
                } else {
                    updateFuture.cancel(false)
                    doneFuture(existingTransaction)
                }
            }
        }
    }

    @VisibleForTesting
    val transactions: List<SignedTransaction>
        get() = database.transaction { snapshot() }

    private fun snapshot(): List<SignedTransaction> {
        return txStorage.content.allPersisted.use {
            it.filter { it.second.status.isVerified() && !it.second.encrypted }.map { it.second.toSignedTx() }.toList()
        }
    }

    // Cache value type to just store the immutable bits of a signed transaction plus conversion helpers
    private data class TxCacheValue(
            val id: SecureHash,
            val txBits: ByteArray,
            val sigs: List<TransactionSignature>,
            val status: TransactionStatus,
            val encrypted: Boolean
    ) {
        constructor(stx: SignedTransaction, status: TransactionStatus) : this(
                stx.id,
                stx.txBits.bytes,
                stx.sigs,
                status,
        false)

        constructor(encryptedTransaction: EncryptedTransaction, status: TransactionStatus) : this(
                encryptedTransaction.id,
                encryptedTransaction.bytes,
                emptyList(),
                status,
                true)

        fun toSignedTx() : SignedTransaction {
            return if (!encrypted) {
                val txBitsAsSerialized = SerializedBytes<CoreTransaction>(txBits)
                SignedTransaction(txBitsAsSerialized, sigs)
            } else {
                throw IllegalArgumentException("Cannot get signed transaction for encrypted tx")
            }
        }

        fun toEncryptedTx() : EncryptedTransaction {
            return if (encrypted) {
                // TODO: EncryptedTransaction will be extended to include verification signature
                EncryptedTransaction(id, txBits)
            } else {
                throw IllegalArgumentException("Cannot get encrypted transaction for signed tx")
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TxCacheValue

            if (!txBits.contentEquals(other.txBits)) return false
            if (sigs != other.sigs) return false
            if (status != other.status) return false
            if (encrypted != other.encrypted) return false

            return true
        }

        override fun hashCode(): Int {
            var result = txBits.contentHashCode()
            result = 31 * result + sigs.hashCode()
            result = 31 * result + status.hashCode()
            result = 31 * result + encrypted.hashCode()
            return result
        }
    }
}
