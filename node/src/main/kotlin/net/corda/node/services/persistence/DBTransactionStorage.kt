package net.corda.node.services.persistence

import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowTransactionMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.toFuture
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.CordaClock
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.node.utilities.WeightBasedAppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.bufferUntilDatabaseCommit
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.nodeapi.internal.persistence.wrapWithDatabaseTransaction
import net.corda.serialization.internal.CordaSerializationEncoding.SNAPPY
import rx.Observable
import rx.subjects.PublishSubject
import java.time.Instant
import java.util.Collections
import javax.persistence.AttributeConverter
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Converter
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table
import kotlin.streams.toList

@Suppress("TooManyFunctions")
class DBTransactionStorage(private val database: CordaPersistence, cacheFactory: NamedCacheFactory,
                           private val clock: CordaClock) : WritableTransactionStorage, SingletonSerializeAsToken() {

    @Suppress("MagicNumber") // database column width
    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}transactions")
    data class DBTransaction(
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

            @Column(name = "signatures")
            val signatures: ByteArray?,

            /**
             * Flow finality metadata used for recovery
             * TODO: create association table solely for Flow metadata and recovery purposes.
             */

            /** X500Name of flow initiator **/
            @Column(name = "initiator")
            val initiator: String? = null,

            /** X500Name of flow participant parties **/
            @Column(name = "participants")
            @Convert(converter = StringListConverter::class)
            val participants: List<String>? = null,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "states_to_record")
            val statesToRecord: StatesToRecord? = null
    )

    enum class TransactionStatus {
        UNVERIFIED,
        VERIFIED,
        MISSING_NOTARY_SIG;

        fun toDatabaseValue(): String {
            return when (this) {
                UNVERIFIED -> "U"
                VERIFIED -> "V"
                MISSING_NOTARY_SIG -> "M"
            }
        }

        fun isVerified(): Boolean {
            return this == VERIFIED
        }

        fun toTransactionStatus(): net.corda.core.flows.TransactionStatus {
            return when(this) {
                UNVERIFIED -> net.corda.core.flows.TransactionStatus.UNVERIFIED
                VERIFIED -> net.corda.core.flows.TransactionStatus.VERIFIED
                MISSING_NOTARY_SIG -> net.corda.core.flows.TransactionStatus.MISSING_NOTARY_SIG
            }
        }

        companion object {
            fun fromDatabaseValue(databaseValue: String): TransactionStatus {
                return when (databaseValue) {
                    "V" -> VERIFIED
                    "U" -> UNVERIFIED
                    "M" -> MISSING_NOTARY_SIG
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

    @Converter
    class StringListConverter : AttributeConverter<List<String>?, String?> {
        override fun convertToDatabaseColumn(stringList: List<String>?): String? {
            return stringList?.let { if (it.isEmpty()) null else it.joinToString(SPLIT_CHAR) }
        }

        override fun convertToEntityAttribute(string: String?): List<String>? {
            return string?.split(SPLIT_CHAR)
        }

        companion object {
            private const val SPLIT_CHAR = ";"
        }
    }

    internal companion object {
        const val TRANSACTION_ALREADY_IN_PROGRESS_WARNING = "trackTransaction is called with an already existing, open DB transaction. As a result, there might be transactions missing from the returned data feed, because of race conditions."

        // Rough estimate for the average of a public key and the transaction metadata - hard to get exact figures here,
        // as public keys can vary in size a lot, and if someone else is holding a reference to the key, it won't add
        // to the memory pressure at all here.
        private const val TRANSACTION_SIGNATURE_OVERHEAD_BYTES = 1024
        private const val TXCACHEVALUE_OVERHEAD_BYTES = 80
        private const val SECUREHASH_OVERHEAD_BYTES = 24

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
                    fromPersistentEntity = { dbTxn ->
                        SecureHash.create(dbTxn.txId) to TxCacheValue(
                                dbTxn.transaction.deserialize(context = contextToUse()),
                                dbTxn.status,
                                dbTxn.signatures?.deserialize(context = contextToUse()),
                                dbTxn.initiator?.let { initiator ->
                                    FlowTransactionMetadata(
                                            CordaX500Name.parse(initiator),
                                            dbTxn.statesToRecord!!,
                                            dbTxn.participants?.let { it.map { CordaX500Name.parse(it) }.toSet() }
                                    )
                                }
                        )
                    },
                    toPersistentEntity = { key: SecureHash, value: TxCacheValue ->
                        DBTransaction(
                                txId = key.toString(),
                                stateMachineRunId = FlowStateMachineImpl.currentStateMachine()?.id?.uuid?.toString(),
                                transaction = value.toSignedTx().serialize(context = contextToUse().withEncoding(SNAPPY)).bytes,
                                status = value.status,
                                timestamp = clock.instant(),
                                signatures = value.sigs.serialize(context = contextToUse().withEncoding(SNAPPY)).bytes,
                                statesToRecord = value.metadata?.statesToRecord,
                                initiator = value.metadata?.initiator?.toString(),
                                participants = value.metadata?.peers?.map { it.toString() }
                        )
                    },
                    persistentEntityClass = DBTransaction::class.java,
                    weighingFunc = { hash, tx -> SECUREHASH_OVERHEAD_BYTES + hash.size + weighTx(tx) }
            )
        }

        private fun weighTx(actTx: TxCacheValue?): Int {
            if (actTx == null) return 0
            return TXCACHEVALUE_OVERHEAD_BYTES + actTx.sigs.sumBy { it.size + TRANSACTION_SIGNATURE_OVERHEAD_BYTES } + actTx.txBits.size
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
                criteriaBuilder.and(updateRoot.get<TransactionStatus>(DBTransaction::status.name).`in`(setOf(TransactionStatus.UNVERIFIED, TransactionStatus.MISSING_NOTARY_SIG))
        )))
        criteriaUpdate.set(updateRoot.get<Instant>(DBTransaction::timestamp.name), clock.instant())
        val update = session.createQuery(criteriaUpdate)
        val rowsUpdated = update.executeUpdate()
        return rowsUpdated != 0
    }

    override fun addTransaction(transaction: SignedTransaction) =
            addTransaction(transaction) {
                updateTransaction(transaction.id)
            }

    override fun addUnnotarisedTransaction(transaction: SignedTransaction, metadata: FlowTransactionMetadata?) =
            database.transaction {
                txStorage.locked {
                    val cacheValue = TxCacheValue(transaction, status = TransactionStatus.MISSING_NOTARY_SIG, metadata = metadata)
                    val added = addWithDuplicatesAllowed(transaction.id, cacheValue)
                    if (added) {
                        logger.info ("Transaction ${transaction.id} recorded as un-notarised.")
                    } else {
                        logger.info("Transaction ${transaction.id} (un-notarised) already exists so no need to record.")
                    }
                    added
                }
            }

    override fun finalizeTransactionWithExtraSignatures(transaction: SignedTransaction, signatures: Collection<TransactionSignature>) =
            addTransaction(transaction) {
                finalizeTransactionWithExtraSignatures(transaction.id, signatures)
            }

    private fun addTransaction(transaction: SignedTransaction, updateFn: (SecureHash) -> Boolean): Boolean {
        return database.transaction {
            txStorage.locked {
                val cachedValue = TxCacheValue(transaction, TransactionStatus.VERIFIED)
                val addedOrUpdated = addOrUpdate(transaction.id, cachedValue) { k, _ -> updateFn(k) }
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

    private fun  finalizeTransactionWithExtraSignatures(txId: SecureHash, signatures: Collection<TransactionSignature>): Boolean {
        return database.transaction {
            txStorage.locked {
                val session = currentDBSession()
                val criteriaBuilder = session.criteriaBuilder
                val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(DBTransaction::class.java)
                val updateRoot = criteriaUpdate.from(DBTransaction::class.java)
                criteriaUpdate.set(updateRoot.get<ByteArray>(DBTransaction::signatures.name), signatures.serialize(context = contextToUse().withEncoding(SNAPPY)).bytes)
                criteriaUpdate.set(updateRoot.get<TransactionStatus>(DBTransaction::status.name), TransactionStatus.VERIFIED)
                criteriaUpdate.where(criteriaBuilder.and(
                        criteriaBuilder.equal(updateRoot.get<String>(DBTransaction::txId.name), txId.toString()),
                        criteriaBuilder.notEqual(updateRoot.get<TransactionStatus>(DBTransaction::status.name), TransactionStatus.VERIFIED)
                ))
                criteriaUpdate.set(updateRoot.get<Instant>(DBTransaction::timestamp.name), clock.instant())
                val update = session.createQuery(criteriaUpdate)
                val rowsUpdated = update.executeUpdate()
                rowsUpdated != 0
            }
        }
    }

    private fun onNewTx(transaction: SignedTransaction): Boolean {
        updatesPublisher.bufferUntilDatabaseCommit().onNext(transaction)
        return true
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? {
        return database.transaction {
            txStorage.content[id]?.let { if (it.status.isVerified()) it.toSignedTx() else null }
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

    override fun getTransactionInternal(id: SecureHash): Pair<SignedTransaction, net.corda.core.flows.TransactionStatus>? {
        return database.transaction {
            txStorage.content[id]?.let { it.toSignedTx() to it.status.toTransactionStatus() }
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
            it.filter { it.second.status.isVerified() }.map { it.second.toSignedTx() }.toList()
        }
    }

    // Cache value type to just store the immutable bits of a signed transaction plus conversion helpers
    private class TxCacheValue(
            val txBits: SerializedBytes<CoreTransaction>,
            val sigs: List<TransactionSignature>,
            val status: TransactionStatus,
            // flow metadata recorded for recovery
            val metadata: FlowTransactionMetadata? = null
    ) {
        constructor(stx: SignedTransaction, status: TransactionStatus) : this(
                stx.txBits,
                Collections.unmodifiableList(stx.sigs),
                status
        )
        constructor(stx: SignedTransaction, status: TransactionStatus, metadata: FlowTransactionMetadata?) : this(
                stx.txBits,
                Collections.unmodifiableList(stx.sigs),
                status,
                metadata
        )
        constructor(stx: SignedTransaction, status: TransactionStatus, sigs: List<TransactionSignature>?, metadata: FlowTransactionMetadata?) : this(
                stx.txBits,
                if (sigs == null) Collections.unmodifiableList(stx.sigs) else Collections.unmodifiableList(stx.sigs + sigs),
                status,
                metadata
        )
        fun toSignedTx() = SignedTransaction(txBits, sigs)
    }
}
