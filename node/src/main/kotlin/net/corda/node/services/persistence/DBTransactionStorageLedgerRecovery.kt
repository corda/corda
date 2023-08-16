package net.corda.node.services.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.DistributionList.SenderDistributionList
import net.corda.core.flows.RecoveryTimeWindow
import net.corda.core.flows.TransactionMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.vault.Sort
import net.corda.core.serialization.CordaSerializable
import net.corda.node.CordaClock
import net.corda.node.services.EncryptionService
import net.corda.node.services.network.PersistentPartyInfoCache
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.hibernate.annotations.Immutable
import java.io.Serializable
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.criteria.Predicate
import kotlin.streams.toList

class DBTransactionStorageLedgerRecovery(private val database: CordaPersistence,
                                         cacheFactory: NamedCacheFactory,
                                         val clock: CordaClock,
                                         private val encryptionService: EncryptionService,
                                         private val partyInfoCache: PersistentPartyInfoCache) : DBTransactionStorage(database, cacheFactory, clock) {
    @Embeddable
    @Immutable
    data class PersistentKey(
            /** PartyId of flow peer **/
            @Column(name = "peer_party_id", nullable = false)
            var peerPartyId: Long,

            @Column(name = "timestamp", nullable = false)
            var timestamp: Instant,

            @Column(name = "timestamp_discriminator", nullable = false)
            var timestampDiscriminator: Int

    ) : Serializable {
        constructor(key: Key) : this(key.partyId, key.timestamp, key.timestampDiscriminator)
    }

    @CordaSerializable
    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}sender_distribution_records")
    data class DBSenderDistributionRecord(
            @EmbeddedId
            var compositeKey: PersistentKey,

            @Column(name = "transaction_id", length = 144, nullable = false)
            var txId: String,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "states_to_record", nullable = false)
            var statesToRecord: StatesToRecord
    ) {
        fun toSenderDistributionRecord() =
            SenderDistributionRecord(
                    SecureHash.parse(this.txId),
                    this.compositeKey.peerPartyId,
                    this.statesToRecord,
                    this.compositeKey.timestamp
            )
    }

    @CordaSerializable
    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}receiver_distribution_records")
    class DBReceiverDistributionRecord(
            @EmbeddedId
            var compositeKey: PersistentKey,

            @Column(name = "transaction_id", length = 144, nullable = false)
            var txId: String,

            /** Encrypted recovery information for sole use by Sender **/
            @Lob
            @Column(name = "distribution_list", nullable = false)
            val distributionList: ByteArray,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "receiver_states_to_record", nullable = false)
            val receiverStatesToRecord: StatesToRecord
) {
        constructor(key: Key, txId: SecureHash, encryptedDistributionList: ByteArray, receiverStatesToRecord: StatesToRecord) :
            this(PersistentKey(key),
                 txId = txId.toString(),
                 distributionList = encryptedDistributionList,
                 receiverStatesToRecord = receiverStatesToRecord
            )

        fun toReceiverDistributionRecord(encryptionService: EncryptionService): ReceiverDistributionRecord {
            val hashedDL = HashedDistributionList.decrypt(this.distributionList, encryptionService)
            return ReceiverDistributionRecord(
                    SecureHash.parse(this.txId),
                    this.compositeKey.peerPartyId,
                    hashedDL.peerHashToStatesToRecord,
                    hashedDL.senderStatesToRecord,
                    this.compositeKey.timestamp
            )
        }
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

    data class TimestampKey(val timestamp: Instant, val timestampDiscriminator: Int)

    class Key(
            val partyId: Long,
            val timestamp: Instant,
            val timestampDiscriminator: Int = nextDiscriminatorNumber.andIncrement
    ) {
        constructor(key: TimestampKey, partyId: Long): this(partyId = partyId, timestamp = key.timestamp, timestampDiscriminator = key.timestampDiscriminator)
        companion object {
            val nextDiscriminatorNumber = AtomicInteger()
        }
    }

    override fun addSenderTransactionRecoveryMetadata(txId: SecureHash, metadata: TransactionMetadata): ByteArray {
        return database.transaction {
            val senderRecordingTimestamp = clock.instant()
            val timeDiscriminator = Key.nextDiscriminatorNumber.andIncrement
            val distributionList = metadata.distributionList as? SenderDistributionList ?: throw IllegalStateException("Expecting SenderDistributionList")
            for (peer in distributionList.peersToStatesToRecord.keys) {
                val senderDistributionRecord = DBSenderDistributionRecord(
                        PersistentKey(Key(TimestampKey(senderRecordingTimestamp, timeDiscriminator), partyInfoCache.getPartyIdByCordaX500Name(peer))),
                        txId.toString(),
                        distributionList.senderStatesToRecord
                )
                session.save(senderDistributionRecord)
            }

            val hashedPeersToStatesToRecord = distributionList.peersToStatesToRecord.mapKeys { (peer) ->
                partyInfoCache.getPartyIdByCordaX500Name(peer)
            }
            val hashedDistributionList = HashedDistributionList(
                    distributionList.senderStatesToRecord,
                    hashedPeersToStatesToRecord,
                    HashedDistributionList.PublicHeader(senderRecordingTimestamp)
            )
            hashedDistributionList.encrypt(encryptionService)
        }
    }

    fun createReceiverTransactionRecoverMetadata(txId: SecureHash,
                                                 senderPartyId: Long,
                                                 senderStatesToRecord: StatesToRecord,
                                                 senderRecords: List<DBSenderDistributionRecord>): List<DBReceiverDistributionRecord> {
        val senderRecordsByTimestampKey = senderRecords.groupBy { TimestampKey(it.compositeKey.timestamp, it.compositeKey.timestampDiscriminator) }
        return senderRecordsByTimestampKey.map {
            val hashedDistributionList = HashedDistributionList(
                    senderStatesToRecord = senderStatesToRecord,
                    peerHashToStatesToRecord = senderRecords.map { it.compositeKey.peerPartyId to it.statesToRecord }.toMap(),
                    senderRecordedTimestamp = it.key.timestamp
            )
            DBReceiverDistributionRecord(
                    compositeKey = PersistentKey(Key(TimestampKey(it.key.timestamp, it.key.timestampDiscriminator), senderPartyId)),
                    txId = txId.toString(),
                    distributionList = cryptoService.encrypt(hashedDistributionList.serialize())
            )
        }
    }

    fun addSenderTransactionRecoveryMetadata(record: DBSenderDistributionRecord) {
        return database.transaction {
            session.save(record)
        }
    }

    override fun addReceiverTransactionRecoveryMetadata(txId: SecureHash,
                                                        sender: CordaX500Name,
                                                        receiver: CordaX500Name,
                                                        receiverStatesToRecord: StatesToRecord,
                                                        encryptedDistributionList: ByteArray) {
        val publicHeader = HashedDistributionList.PublicHeader.unauthenticatedDeserialise(encryptedDistributionList, encryptionService)
        database.transaction {
            val receiverDistributionRecord = DBReceiverDistributionRecord(
                    Key(partyInfoCache.getPartyIdByCordaX500Name(sender), publicHeader.senderRecordedTimestamp),
                    txId,
                    encryptedDistributionList,
                    receiverStatesToRecord
            )
            session.save(receiverDistributionRecord)
        }
    }

    fun addReceiverTransactionRecoveryMetadata(record: DBReceiverDistributionRecord) {
        return database.transaction {
            session.save(record)
        }
    }

    override fun removeUnnotarisedTransaction(id: SecureHash): Boolean {
        return database.transaction {
            super.removeUnnotarisedTransaction(id)
            val criteriaBuilder = session.criteriaBuilder
            val deleteSenderDistributionRecords = criteriaBuilder.createCriteriaDelete(DBSenderDistributionRecord::class.java)
            val root = deleteSenderDistributionRecords.from(DBSenderDistributionRecord::class.java)
            deleteSenderDistributionRecords.where(criteriaBuilder.equal(root.get<String>(DBSenderDistributionRecord::txId.name), id.toString()))
            val deletedSenderDistributionRecords = session.createQuery(deleteSenderDistributionRecords).executeUpdate() != 0
            val deleteReceiverDistributionRecords = criteriaBuilder.createCriteriaDelete(DBReceiverDistributionRecord::class.java)
            val rootReceiverDistributionRecord = deleteReceiverDistributionRecords.from(DBReceiverDistributionRecord::class.java)
            deleteReceiverDistributionRecords.where(criteriaBuilder.equal(rootReceiverDistributionRecord.get<String>(DBReceiverDistributionRecord::txId.name), id.toString()))
            val deletedReceiverDistributionRecords = session.createQuery(deleteReceiverDistributionRecords).executeUpdate() != 0
            deletedSenderDistributionRecords || deletedReceiverDistributionRecords
        }
    }

    fun queryDistributionRecords(timeWindow: RecoveryTimeWindow,
                               recordType: DistributionRecordType = DistributionRecordType.ALL,
                               excludingTxnIds: Set<SecureHash> = emptySet(),
                               orderByTimestamp: Sort.Direction? = null
    ): DistributionRecords {
        return when(recordType) {
            DistributionRecordType.SENDER ->
                DistributionRecords(senderRecords =
                    querySenderDistributionRecords(timeWindow, excludingTxnIds = excludingTxnIds, orderByTimestamp = orderByTimestamp))
            DistributionRecordType.RECEIVER ->
                DistributionRecords(receiverRecords =
                    queryReceiverDistributionRecords(timeWindow, excludingTxnIds = excludingTxnIds, orderByTimestamp = orderByTimestamp))
            DistributionRecordType.ALL ->
                DistributionRecords(senderRecords =
                    querySenderDistributionRecords(timeWindow, excludingTxnIds = excludingTxnIds, orderByTimestamp = orderByTimestamp),
                                    receiverRecords =
                    queryReceiverDistributionRecords(timeWindow, excludingTxnIds = excludingTxnIds, orderByTimestamp = orderByTimestamp))
        }
    }

    @Suppress("SpreadOperator")
    fun querySenderDistributionRecords(timeWindow: RecoveryTimeWindow,
                                       peers: Set<CordaX500Name> = emptySet(),
                                       excludingTxnIds: Set<SecureHash> = emptySet(),
                                       orderByTimestamp: Sort.Direction? = null
                             ): List<DBSenderDistributionRecord> {
        return database.transaction {
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(DBSenderDistributionRecord::class.java)
            val txnMetadata = criteriaQuery.from(DBSenderDistributionRecord::class.java)
            val predicates = mutableListOf<Predicate>()
            val compositeKey = txnMetadata.get<PersistentKey>("compositeKey")
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(compositeKey.get<Instant>(PersistentKey::timestamp.name), timeWindow.fromTime))
            predicates.add(criteriaBuilder.and(criteriaBuilder.lessThanOrEqualTo(compositeKey.get<Instant>(PersistentKey::timestamp.name), timeWindow.untilTime)))
            if (excludingTxnIds.isNotEmpty()) {
                predicates.add(criteriaBuilder.and(criteriaBuilder.not(txnMetadata.get<String>(DBSenderDistributionRecord::txId.name).`in`(
                        excludingTxnIds.map { it.toString() }))))
            }
            if (peers.isNotEmpty()) {
                val peerPartyIds = peers.map { partyInfoCache.getPartyIdByCordaX500Name(it) }
                predicates.add(criteriaBuilder.and(compositeKey.get<Long>(PersistentKey::peerPartyId.name).`in`(peerPartyIds)))
            }
            criteriaQuery.where(*predicates.toTypedArray())
            // optionally order by timestamp
            orderByTimestamp?.let {
                val orderCriteria =
                        when (orderByTimestamp) {
                            // when adding column position of 'group by' shift in case columns were removed
                            Sort.Direction.ASC -> criteriaBuilder.asc(compositeKey.get<Instant>(PersistentKey::timestamp.name))
                            Sort.Direction.DESC -> criteriaBuilder.desc(compositeKey.get<Instant>(PersistentKey::timestamp.name))
                        }
                criteriaQuery.orderBy(orderCriteria)
            }
            session.createQuery(criteriaQuery).stream().use { results ->
                results.map { it.toSenderDistributionRecord() }.toList()
            }
        }
    }

    fun querySenderDistributionRecordsByTxId(txId: SecureHash): List<DBSenderDistributionRecord> {
        return database.transaction {
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(DBSenderDistributionRecord::class.java)
            val txnMetadata = criteriaQuery.from(DBSenderDistributionRecord::class.java)
            criteriaQuery.where(criteriaBuilder.equal(txnMetadata.get<String>(DBSenderDistributionRecord::txId.name), txId.toString()))
            val results = session.createQuery(criteriaQuery).stream()
            results.toList()
        }
    }

    @Suppress("SpreadOperator")
    fun queryReceiverDistributionRecords(timeWindow: RecoveryTimeWindow,
                                       initiators: Set<CordaX500Name> = emptySet(),
                                       excludingTxnIds: Set<SecureHash> = emptySet(),
                                       orderByTimestamp: Sort.Direction? = null
    ): List<DBReceiverDistributionRecord> {
        return database.transaction {
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(DBReceiverDistributionRecord::class.java)
            val txnMetadata = criteriaQuery.from(DBReceiverDistributionRecord::class.java)
            val predicates = mutableListOf<Predicate>()
            val compositeKey = txnMetadata.get<PersistentKey>("compositeKey")
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(compositeKey.get<Instant>(PersistentKey::timestamp.name), timeWindow.fromTime))
            predicates.add(criteriaBuilder.and(criteriaBuilder.lessThanOrEqualTo(compositeKey.get<Instant>(PersistentKey::timestamp.name), timeWindow.untilTime)))
            if (excludingTxnIds.isNotEmpty()) {
                predicates.add(criteriaBuilder.and(criteriaBuilder.not(txnMetadata.get<String>(DBSenderDistributionRecord::txId.name).`in`(
                        excludingTxnIds.map { it.toString() }))))
            }
            if (initiators.isNotEmpty()) {
                val initiatorPartyIds = initiators.map { partyInfoCache.getPartyIdByCordaX500Name(it) }
                predicates.add(criteriaBuilder.and(compositeKey.get<Long>(PersistentKey::peerPartyId.name).`in`(initiatorPartyIds)))
            }
            criteriaQuery.where(*predicates.toTypedArray())
            // optionally order by timestamp
            orderByTimestamp?.let {
                val orderCriteria =
                        when (orderByTimestamp) {
                            // when adding column position of 'group by' shift in case columns were removed
                            Sort.Direction.ASC -> criteriaBuilder.asc(compositeKey.get<Instant>(PersistentKey::timestamp.name))
                            Sort.Direction.DESC -> criteriaBuilder.desc(compositeKey.get<Instant>(PersistentKey::timestamp.name))
                        }
                criteriaQuery.orderBy(orderCriteria)
            }
            session.createQuery(criteriaQuery).stream().use { results ->
                results.map { it.toReceiverDistributionRecord(encryptionService) }.toList()
            }
        }
    }
}


@CordaSerializable
class DistributionRecords(
        val senderRecords: List<DBTransactionStorageLedgerRecovery.DBSenderDistributionRecord> = emptyList(),
        val receiverRecords: List<DBTransactionStorageLedgerRecovery.DBReceiverDistributionRecord> = emptyList()
) {
    init {
        assert(senderRecords.isNotEmpty() || receiverRecords.isNotEmpty()) { "Must set senderRecords or receiverRecords or both." }
    }

    val size = senderRecords.size + receiverRecords.size
}

@CordaSerializable
open class DistributionRecord(
        open val txId: SecureHash,
        open val statesToRecord: StatesToRecord,
        open val timestamp: Instant
)

@CordaSerializable
data class SenderDistributionRecord(
        override val txId: SecureHash,
        val peerPartyId: Long,     // CordaX500Name hashCode()
        override val statesToRecord: StatesToRecord,
        override val timestamp: Instant
) : DistributionRecord(txId, statesToRecord, timestamp)

@CordaSerializable
data class ReceiverDistributionRecord(
        override val txId: SecureHash,
        val initiatorPartyId: Long,     // CordaX500Name hashCode()
        val peersToStatesToRecord: Map<Long, StatesToRecord>,   // CordaX500Name hashCode() -> StatesToRecord
        override val statesToRecord: StatesToRecord,
        override val timestamp: Instant
) : DistributionRecord(txId, statesToRecord, timestamp)

@CordaSerializable
enum class DistributionRecordType {
    SENDER, RECEIVER, ALL
}
