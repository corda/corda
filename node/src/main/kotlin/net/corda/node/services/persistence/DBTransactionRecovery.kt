package net.corda.node.services.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowTransactionMetadata
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.CordaClock
import net.corda.node.services.network.RecoveryPartyInfoCache
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.serialization.internal.CordaSerializationEncoding
import java.time.Instant
import java.time.Instant.now
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table

class DBTransactionRecovery(private val database: CordaPersistence, cacheFactory: NamedCacheFactory,
                            val clock: CordaClock,
                            private val cryptoService: CryptoService,
                            private val partyInfoCache: RecoveryPartyInfoCache) : DBTransactionStorage(database, cacheFactory, clock) {
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
            val initiatorPartyId: Long?,

            /** PartyId of flow peers **/
            @Lob
            @Column(name = "peer_party_ids", nullable = false)
            val peerPartyIds: ByteArray,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "states_to_record", nullable = false)
            var statesToRecord: StatesToRecord,

            @Column(name = "timestamp", nullable = false)
            val timestamp: Instant
    ) {
        constructor(txId: SecureHash, initiatorPartyId: Long?, peerPartyIds: Set<Long>, statesToRecord: StatesToRecord, cryptoService: CryptoService) :
                this(txId = txId.toString(), initiatorPartyId = initiatorPartyId,
                        peerPartyIds =
                            if (initiatorPartyId == null)
                                peerPartyIds.serialize(context = contextToUse().withEncoding(CordaSerializationEncoding.SNAPPY)).bytes
                            else
                                cryptoService.encrypt(peerPartyIds.serialize(context = contextToUse().withEncoding(CordaSerializationEncoding.SNAPPY)).bytes),
                        statesToRecord = statesToRecord,
                        timestamp = now()
                )
        fun toTransactionRecoveryMetadata(cryptoService: CryptoService) =
                TransactionRecoveryMetadata(
                        SecureHash.parse(this.txId),
                        this.initiatorPartyId,
                        if (this.isSender())
                            this.peerPartyIds.deserialize(context = contextToUse())
                        else
                            cryptoService.decrypt(this.peerPartyIds).deserialize(context = contextToUse()),
                        this.statesToRecord,
                        this.timestamp

                )

        fun isSender(): Boolean {
            return initiatorPartyId == null
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

    private val txRecoveryMetadataStorage = ThreadBox(createTransactionRecoveryMap(cacheFactory))

    internal class TxRecoveryCacheValue(
            val txId: SecureHash,
            val metadata: TransactionRecoveryMetadata
    )

    private fun createTransactionRecoveryMap(cacheFactory: NamedCacheFactory)
            : AppendOnlyPersistentMapBase<SecureHash, TxRecoveryCacheValue, DBRecoveryTransactionMetadata, String> {
        return AppendOnlyPersistentMap<SecureHash, TxRecoveryCacheValue, DBRecoveryTransactionMetadata, String>(
                cacheFactory = cacheFactory,
                name = "DBTransactionRecovery_transactions",
                toPersistentEntityKey = SecureHash::toString,
                fromPersistentEntity = { dbTxn ->
                    val txId = SecureHash.create(dbTxn.txId)
                    txId to TxRecoveryCacheValue(txId, dbTxn.toTransactionRecoveryMetadata(cryptoService))
                },
                toPersistentEntity = { key: SecureHash, value: TxRecoveryCacheValue ->
                    DBRecoveryTransactionMetadata(key, value.metadata.initiatorPartyId, value.metadata.peerPartyIds, value.metadata.statesToRecord, cryptoService)
                },
                persistentEntityClass = DBRecoveryTransactionMetadata::class.java
        )
    }

    override fun addUnnotarisedTransaction(transaction: SignedTransaction, metadata: FlowTransactionMetadata): Boolean {
        return addTransaction(transaction, TransactionStatus.IN_FLIGHT) {
            addTransactionRecoveryMetadata(transaction.id, metadata) { false }
        }
    }

    override fun finalizeTransaction(transaction: SignedTransaction, metadata: FlowTransactionMetadata) =
            addTransaction(transaction) {
                addTransactionRecoveryMetadata(transaction.id, metadata) { false }
            }

    private fun addTransactionRecoveryMetadata(txId: SecureHash, metadata: FlowTransactionMetadata,
                                               updateFn: (SecureHash) -> Boolean): Boolean {
        return database.transaction {
            txRecoveryMetadataStorage.locked {
                val cachedValue = TxRecoveryCacheValue(txId,
                        TransactionRecoveryMetadata(txId,
                                partyInfoCache.getPartyIdByCordaX500Name(metadata.initiator),
                                metadata.peers?.map { partyInfoCache.getPartyIdByCordaX500Name(it) }?.toSet() ?: emptySet(),
                                metadata.statesToRecord ?: StatesToRecord.ONLY_RELEVANT,
                                now()))
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

@CordaSerializable
data class TransactionRecoveryMetadata(
        val txId: SecureHash,
        val initiatorPartyId: Long?,    // CordaX500Name hashCode()
        val peerPartyIds: Set<Long>,    // CordaX500Name hashCode()
        val statesToRecord: StatesToRecord,
        val timestamp: Instant
)

class CryptoService {
    fun encrypt(bytes: ByteArray): ByteArray {
//        TODO("Not yet implemented")
        return bytes
    }

    fun decrypt(bytes: ByteArray): ByteArray {
//        TODO("Not yet implemented")
        return bytes
    }
}
