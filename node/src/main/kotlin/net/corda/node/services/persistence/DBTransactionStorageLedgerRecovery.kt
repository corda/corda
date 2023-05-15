package net.corda.node.services.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.TransactionMetadata
import net.corda.core.flows.RecoveryTimeWindow
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.vault.Sort
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.CordaClock
import net.corda.node.services.network.PersistentPartyInfoCache
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.serialization.internal.CordaSerializationEncoding
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.criteria.Predicate
import kotlin.streams.toList

class DBTransactionStorageLedgerRecovery(private val database: CordaPersistence, cacheFactory: NamedCacheFactory,
                                         val clock: CordaClock,
                                         private val cryptoService: CryptoService,
                                         private val partyInfoCache: PersistentPartyInfoCache) : DBTransactionStorage(database, cacheFactory, clock) {
    internal companion object {
        private val logger = contextLogger()
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}recovery_transaction_metadata")
    data class DBRecoveryTransactionMetadata(
            @Id
            @Column(name = "tx_id", length = 144, nullable = false)
            var txId: String,

            /** PartyId of flow initiator **/
            @Column(name = "initiator_party_id", nullable = true)
            val initiatorPartyId: Long,

            /** PartyId of flow peers **/
            @Lob
            @Column(name = "peer_party_ids", nullable = false)
            val peerPartyIds: ByteArray,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "states_to_record", nullable = false)
            var statesToRecord: StatesToRecord,

            @Column(name = "timestamp", nullable = false)
            val timestamp: Instant,

            /** Sender or Receiver distribution record **/
            @Column(name = "is_sender", nullable = false)
            val isSender: Boolean
    ) {
        constructor(txId: SecureHash, isSender: Boolean, initiatorPartyId: Long, peerPartyIds: Set<Long>, statesToRecord: StatesToRecord, clock: CordaClock, cryptoService: CryptoService) :
                this(txId = txId.toString(), initiatorPartyId = initiatorPartyId,
                        peerPartyIds =
                            if (isSender)
                                peerPartyIds.serialize(context = contextToUse().withEncoding(CordaSerializationEncoding.SNAPPY)).bytes
                            else
                                cryptoService.encrypt(peerPartyIds.serialize(context = contextToUse().withEncoding(CordaSerializationEncoding.SNAPPY)).bytes),
                        statesToRecord = statesToRecord,
                        timestamp = clock.instant(),
                        isSender = isSender
                )

        fun toTransactionRecoveryMetadata(cryptoService: CryptoService) =
                DistributionRecord(
                        SecureHash.parse(this.txId),
                        this.initiatorPartyId,
                        if (this.isSender)
                            this.peerPartyIds.deserialize(context = contextToUse())
                        else
                            cryptoService.decrypt(this.peerPartyIds).deserialize(context = contextToUse()),
                        this.statesToRecord,
                        this.timestamp,
                        this.isSender
                )
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}recovery_party_info")
    data class DBRecoveryPartyInfo(
            @Id
            /** CordaX500Name hashCode() **/
            @Column(name = "party_id", nullable = false)
            var partyId: Long,

            /** CordaX500Name of party **/
            @Column(name = "party_name", nullable = false)
            val partyName: String
    )

    private val txRecoveryMetadataStorage = ThreadBox(createTransactionRecoveryMap(cacheFactory))

    internal class TxRecoveryCacheValue(
            val txId: SecureHash,
            val metadata: DistributionRecord
    )

    private fun createTransactionRecoveryMap(cacheFactory: NamedCacheFactory)
            : AppendOnlyPersistentMapBase<SecureHash, TxRecoveryCacheValue, DBRecoveryTransactionMetadata, String> {
        return AppendOnlyPersistentMap(
                cacheFactory = cacheFactory,
                name = "DBTransactionRecovery_recoveryMetadata",
                toPersistentEntityKey = SecureHash::toString,
                fromPersistentEntity = { dbTxn ->
                    val txId = SecureHash.create(dbTxn.txId)
                    txId to TxRecoveryCacheValue(txId, dbTxn.toTransactionRecoveryMetadata(cryptoService))
                },
                toPersistentEntity = { key: SecureHash, value: TxRecoveryCacheValue ->
                    DBRecoveryTransactionMetadata(key, value.metadata.isSender, value.metadata.initiatorPartyId, value.metadata.peerPartyIds, value.metadata.statesToRecord, clock, cryptoService)
                },
                persistentEntityClass = DBRecoveryTransactionMetadata::class.java
        )
    }

    override fun addUnnotarisedTransaction(transaction: SignedTransaction, metadata: TransactionMetadata, isInitiator: Boolean): Boolean {
        return addTransaction(transaction, TransactionStatus.IN_FLIGHT) {
            addTransactionRecoveryMetadata(transaction.id, metadata, isInitiator, clock) { false }
        }
    }

    override fun finalizeTransaction(transaction: SignedTransaction, metadata: TransactionMetadata, isInitiator: Boolean) =
            addTransaction(transaction) {
                addTransactionRecoveryMetadata(transaction.id, metadata, isInitiator, clock) { false }
            }

    override fun removeUnnotarisedTransaction(id: SecureHash): Boolean {
        return database.transaction {
            super.removeUnnotarisedTransaction(id)
            val criteriaBuilder = session.criteriaBuilder
            val delete = criteriaBuilder.createCriteriaDelete(DBRecoveryTransactionMetadata::class.java)
            val root = delete.from(DBRecoveryTransactionMetadata::class.java)
            delete.where(criteriaBuilder.equal(root.get<String>(DBRecoveryTransactionMetadata::txId.name), id.toString()))
            if (session.createQuery(delete).executeUpdate() != 0) {
                txRecoveryMetadataStorage.locked {
                    txRecoveryMetadataStorage.content.clear(id)
                    txRecoveryMetadataStorage.content[id]
                    logger.debug { "Recovery metadata has been removed for un-notarised transaction $id." }
                }
                true
            } else false
        }
    }

    @Suppress("SpreadOperator")
    fun queryForTransactions(timeWindow: RecoveryTimeWindow,
                             recordType: DistributionRecordType = DistributionRecordType.ALL,
                             orderByTimestamp: Sort.Direction? = null,
                             excludingTxnIds: Set<SecureHash>? = null): List<DistributionRecord> {
        return database.transaction {
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(DBRecoveryTransactionMetadata::class.java)
            val txnMetadata = criteriaQuery.from(DBRecoveryTransactionMetadata::class.java)
            val predicates = mutableListOf<Predicate>()
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(txnMetadata.get<Instant>(DBRecoveryTransactionMetadata::timestamp.name), timeWindow.fromTime))
            predicates.add(criteriaBuilder.and(criteriaBuilder.lessThanOrEqualTo(txnMetadata.get<Instant>(DBRecoveryTransactionMetadata::timestamp.name), timeWindow.untilTime)))
            excludingTxnIds?.let { excludingTxnIds ->
                predicates.add(criteriaBuilder.and(criteriaBuilder.notEqual(txnMetadata.get<String>(DBRecoveryTransactionMetadata::txId.name),
                        excludingTxnIds.map { it.toString() })))
            }
            if (recordType != DistributionRecordType.ALL) {
                val isSender = (recordType == DistributionRecordType.SENDER)
                predicates.add(criteriaBuilder.and(criteriaBuilder.equal(txnMetadata.get<Boolean>(DBRecoveryTransactionMetadata::isSender.name), isSender)))
            }
            criteriaQuery.where(*predicates.toTypedArray())
            // optionally order by timestamp
            orderByTimestamp?.let {
                val orderCriteria =
                        when (orderByTimestamp) {
                            // when adding column position of 'group by' shift in case columns were removed
                            Sort.Direction.ASC -> criteriaBuilder.asc(txnMetadata.get<Instant>(DBRecoveryTransactionMetadata::timestamp.name))
                            Sort.Direction.DESC -> criteriaBuilder.desc(txnMetadata.get<Instant>(DBRecoveryTransactionMetadata::timestamp.name))
                        }
                criteriaQuery.orderBy(orderCriteria)
            }
            val results = session.createQuery(criteriaQuery).stream()
            results.map { it.toTransactionRecoveryMetadata(cryptoService) }.toList()
        }
    }

    private fun addTransactionRecoveryMetadata(txId: SecureHash, metadata: TransactionMetadata, isInitiator: Boolean, clock: CordaClock,
                                               updateFn: (SecureHash) -> Boolean): Boolean {
        return database.transaction {
            txRecoveryMetadataStorage.locked {
                val cachedValue = TxRecoveryCacheValue(txId,
                        DistributionRecord(txId,
                                partyInfoCache.getPartyIdByCordaX500Name(metadata.initiator),
                                metadata.peers?.map { partyInfoCache.getPartyIdByCordaX500Name(it) }?.toSet() ?: emptySet(),
                                metadata.statesToRecord ?: StatesToRecord.ONLY_RELEVANT,
                                clock.instant(),
                                isInitiator))
                val addedOrUpdated = addOrUpdate(txId, cachedValue) { k, _ -> updateFn(k) }
                if (addedOrUpdated) {
                    logger.debug { "Transaction recovery metadata for $txId has been recorded." }
                } else {
                    logger.debug { "Transaction recovery metadata for $txId is already recorded, so no need to re-record." }
                }
                false
            }
        }
    }
}

// TO DO: https://r3-cev.atlassian.net/browse/ENT-9876
private fun CryptoService.decrypt(bytes: ByteArray): ByteArray {
    return bytes
}

// TO DO: https://r3-cev.atlassian.net/browse/ENT-9876
private fun CryptoService.encrypt(bytes: ByteArray): ByteArray {
    return bytes
}

// Sender stores itself as initiatorPartyId, cleartext peerPartyIds
// Receiver receives sender as initiatorPartyId, encrypted peerPartyIds
@CordaSerializable
data class DistributionRecord(
        val txId: SecureHash,
        val initiatorPartyId: Long,     // CordaX500Name hashCode()
        val peerPartyIds: Set<Long>,    // (encrypted) CordaX500Name hashCode()
        val statesToRecord: StatesToRecord,
        val timestamp: Instant,
        val isSender: Boolean
)

@CordaSerializable
enum class DistributionRecordType {
    SENDER, RECEIVER, ALL
}



