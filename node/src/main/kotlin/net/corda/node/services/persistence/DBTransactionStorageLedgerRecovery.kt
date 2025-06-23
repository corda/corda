package net.corda.node.services.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.DistributionList.ReceiverDistributionList
import net.corda.core.flows.DistributionList.SenderDistributionList
import net.corda.core.flows.DistributionRecordKey
import net.corda.core.flows.ReceiverDistributionRecord
import net.corda.core.flows.SenderDistributionRecord
import net.corda.core.flows.TransactionMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.OpaqueBytes
import net.corda.node.CordaClock
import net.corda.node.services.EncryptionService
import net.corda.node.services.network.PersistentPartyInfoCache
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.hibernate.annotations.Immutable
import java.io.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table

class DBTransactionStorageLedgerRecovery(private val database: CordaPersistence,
                                         cacheFactory: NamedCacheFactory,
                                         val clock: CordaClock,
                                         private val encryptionService: EncryptionService,
                                         private val partyInfoCache: PersistentPartyInfoCache) : DBTransactionStorage(database, cacheFactory, clock) {
    @Embeddable
    @Immutable
    data class PersistentKey(
            @Column(name = "transaction_id", length = 144, nullable = false)
            var txId: String,

            @Column(name = "peer_party_id", length = 144, nullable = false)
            var peerPartyId: String,

            @Column(name = "timestamp", nullable = false)
            var timestamp: Instant,

            @Column(name = "timestamp_discriminator", nullable = false)
            var timestampDiscriminator: Int

    ) : Serializable {
        constructor(key: Key) : this(key.txId.toString(), key.partyId.toString(), key.timestamp, key.timestampDiscriminator)
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}sender_distr_recs")
    data class DBSenderDistributionRecord(
            @EmbeddedId
            var compositeKey: PersistentKey,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "sender_states_to_record", nullable = false)
            var senderStatesToRecord: StatesToRecord,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "receiver_states_to_record", nullable = false)
            var receiverStatesToRecord: StatesToRecord
    ) {
        fun key() = DistributionRecordKey(
                SecureHash.parse(this.compositeKey.txId),
                this.compositeKey.timestamp,
                this.compositeKey.timestampDiscriminator)

        fun toSenderDistributionRecord() =
            SenderDistributionRecord(
                    SecureHash.parse(this.compositeKey.txId),
                    SecureHash.parse(this.compositeKey.peerPartyId),
                    this.compositeKey.timestamp,
                    this.compositeKey.timestampDiscriminator,
                    this.senderStatesToRecord,
                    this.receiverStatesToRecord
            )
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}receiver_distr_recs")
    data class DBReceiverDistributionRecord(
            @EmbeddedId
            var compositeKey: PersistentKey,

            /** Encrypted recovery information for sole use by Sender **/
            @Lob
            @Column(name = "distribution_list", nullable = false)
            val distributionList: ByteArray,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "receiver_states_to_record", nullable = false)
            val receiverStatesToRecord: StatesToRecord
) {
        constructor(key: Key, encryptedDistributionList: ByteArray, receiverStatesToRecord: StatesToRecord) :
            this(PersistentKey(key),
                 distributionList = encryptedDistributionList,
                 receiverStatesToRecord = receiverStatesToRecord
            )

        fun key() = DistributionRecordKey(
                        SecureHash.parse(this.compositeKey.txId),
                        this.compositeKey.timestamp,
                        this.compositeKey.timestampDiscriminator)

        fun toReceiverDistributionRecord(): ReceiverDistributionRecord {
            return ReceiverDistributionRecord(
                    SecureHash.parse(this.compositeKey.txId),
                    SecureHash.parse(this.compositeKey.peerPartyId),
                    this.compositeKey.timestamp,
                    this.compositeKey.timestampDiscriminator,
                    OpaqueBytes(this.distributionList),
                    this.receiverStatesToRecord
            )
        }
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}recovery_party_info")
    data class DBRecoveryPartyInfo(
            @Id
            /** CordaX500Name hashCode() **/
            @Column(name = "party_id", length = 144, nullable = false)
            var partyId: String,

            /** CordaX500Name of party **/
            @Column(name = "party_name", nullable = false)
            val partyName: String
    )

    class Key(
            val txId: SecureHash,
            val partyId: SecureHash,
            val timestamp: Instant,
            val timestampDiscriminator: Int = nextDiscriminatorNumber.andIncrement
    ) {
        companion object {
            val nextDiscriminatorNumber = AtomicInteger()
        }
    }

    override fun addSenderTransactionRecoveryMetadata(txId: SecureHash, metadata: TransactionMetadata): ByteArray {
        return database.transaction {
            val senderRecordingTimestamp = clock.instant().truncatedTo(ChronoUnit.SECONDS)
            val timeDiscriminator = Key.nextDiscriminatorNumber.andIncrement
            val distributionList = metadata.distributionList as? SenderDistributionList ?: throw IllegalStateException("Expecting SenderDistributionList")
            distributionList.peersToStatesToRecord.map { (peerCordaX500Name, peerStatesToRecord) ->
                val senderDistributionRecord = DBSenderDistributionRecord(
                        PersistentKey(Key(txId,
                                partyInfoCache.getPartyIdByCordaX500Name(peerCordaX500Name),
                                senderRecordingTimestamp, timeDiscriminator)),
                        distributionList.senderStatesToRecord,
                        peerStatesToRecord)
                session.save(senderDistributionRecord)
            }
            val hashedPeersToStatesToRecord = distributionList.peersToStatesToRecord.mapKeys { (peer) ->
                partyInfoCache.getPartyIdByCordaX500Name(peer)
            }
            val hashedDistributionList = HashedDistributionList(
                    distributionList.senderStatesToRecord,
                    hashedPeersToStatesToRecord,
                    HashedDistributionList.PublicHeader(senderRecordingTimestamp, timeDiscriminator)
            )
            hashedDistributionList.encrypt(encryptionService)
        }
    }

    override fun addReceiverTransactionRecoveryMetadata(txId: SecureHash,
                                                        sender: CordaX500Name,
                                                        metadata: TransactionMetadata) {
        when (metadata.distributionList) {
            is ReceiverDistributionList -> {
                val distributionList = metadata.distributionList as ReceiverDistributionList
                val publicHeader = HashedDistributionList.PublicHeader.unauthenticatedDeserialise(distributionList.opaqueData, encryptionService)
                database.transaction {
                    val receiverDistributionRecord = DBReceiverDistributionRecord(
                            Key(txId, partyInfoCache.getPartyIdByCordaX500Name(sender), publicHeader.senderRecordedTimestamp, publicHeader.timeDiscriminator),
                            distributionList.opaqueData,
                            distributionList.receiverStatesToRecord
                    )
                    session.saveOrUpdate(receiverDistributionRecord)
                }
            }
            else -> throw IllegalStateException("Expecting ReceiverDistributionList")
        }
    }

    override fun removeUnnotarisedTransaction(id: SecureHash): Boolean {
        return database.transaction {
            super.removeUnnotarisedTransaction(id)
            val criteriaBuilder = session.criteriaBuilder
            val deleteSenderDistributionRecords = criteriaBuilder.createCriteriaDelete(DBSenderDistributionRecord::class.java)
            val rootSender = deleteSenderDistributionRecords.from(DBSenderDistributionRecord::class.java)
            val compositeKeySender = rootSender.get<PersistentKey>("compositeKey")
            deleteSenderDistributionRecords.where(criteriaBuilder.equal(compositeKeySender.get<String>(PersistentKey::txId.name), id.toString()))
            val deletedSenderDistributionRecords = session.createQuery(deleteSenderDistributionRecords).executeUpdate() != 0
            val deleteReceiverDistributionRecords = criteriaBuilder.createCriteriaDelete(DBReceiverDistributionRecord::class.java)
            val rootReceiver = deleteReceiverDistributionRecords.from(DBReceiverDistributionRecord::class.java)
            val compositeKeyReceiver = rootReceiver.get<PersistentKey>("compositeKey")
            deleteReceiverDistributionRecords.where(criteriaBuilder.equal(compositeKeyReceiver.get<String>(PersistentKey::txId.name), id.toString()))
            val deletedReceiverDistributionRecords = session.createQuery(deleteReceiverDistributionRecords).executeUpdate() != 0
            deletedSenderDistributionRecords || deletedReceiverDistributionRecords
        }
    }

    fun decryptHashedDistributionList(encryptedBytes: ByteArray): HashedDistributionList {
        return HashedDistributionList.decrypt(encryptedBytes, encryptionService)
    }
}

