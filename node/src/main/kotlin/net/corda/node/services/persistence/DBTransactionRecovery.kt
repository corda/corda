package net.corda.node.services.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowTransactionMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.ThreadBox
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.CordaClock
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.ForeignKey
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToMany
import javax.persistence.OneToOne
import javax.persistence.PrimaryKeyJoinColumn
import javax.persistence.Table

@Suppress("TooManyFunctions")
class DBTransactionRecovery(private val database: CordaPersistence, cacheFactory: NamedCacheFactory,
                            clock: CordaClock) : DBTransactionStorage(database, cacheFactory, clock) {
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
            @OneToOne(fetch = FetchType.LAZY)
//            @PrimaryKeyJoinColumn
            var initiator: DBRecoveryPartyInfo,

            /** PartyId of flow peers **/
            @Column(name = "peers", nullable = false)
            @OneToMany(cascade = [(CascadeType.ALL)], orphanRemoval = true)
//            @JoinColumn(name = "party_id", foreignKey = ForeignKey(name = "FK__party_info__peer_party_id"))
            val peers: List<DBRecoveryPartyInfo>,

            /** states to record: NONE, ALL_VISIBLE, ONLY_RELEVANT */
            @Column(name = "states_to_record", nullable = false)
            var statesToRecord: StatesToRecord
    )

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}recovery_party_info")
    data class DBRecoveryPartyInfo(
            @Id
            @GeneratedValue
            @Column(name = "party_id", nullable = false)
            var partyId: Int,

            /** X500Name of party **/
            @Column(name = "party_name", nullable = false)
            val partyName: String
    )

    private val txRecoveryMetadataStorage = ThreadBox(createTransactionRecoveryMap(cacheFactory))

    internal class TxRecoveryCacheValue(
            val txId: SecureHash,
            val metadata: FlowTransactionMetadata? = null
    )

    private fun createTransactionRecoveryMap(cacheFactory: NamedCacheFactory)
            : AppendOnlyPersistentMapBase<SecureHash, TxRecoveryCacheValue, DBRecoveryTransactionMetadata, String> {
        return AppendOnlyPersistentMap<SecureHash, TxRecoveryCacheValue, DBRecoveryTransactionMetadata, String>(
                cacheFactory = cacheFactory,
                name = "DBTransactionRecovery_transactions",
                toPersistentEntityKey = SecureHash::toString,
                fromPersistentEntity = { dbTxn ->
                    SecureHash.create(dbTxn.txId) to TxRecoveryCacheValue(
                            SecureHash.create(dbTxn.txId),
                            FlowTransactionMetadata(
                                    CordaX500Name.parse(dbTxn.initiator.partyName),
                                    dbTxn.statesToRecord,
                                    dbTxn.peers.map { CordaX500Name.parse(it.partyName) }.toSet()
                            )
                    )
                },
                toPersistentEntity = { key: SecureHash, value: TxRecoveryCacheValue ->
                    DBRecoveryTransactionMetadata(
                            txId = key.toString(),
                            initiator = DBRecoveryPartyInfo(0, value.metadata?.initiator.toString()),
                            peers = value.metadata?.peers?.map { DBRecoveryPartyInfo(0, it.toString()) } ?: emptyList(),
                            statesToRecord = value.metadata?.statesToRecord ?: StatesToRecord.ONLY_RELEVANT
                    )
                },
                persistentEntityClass = DBRecoveryTransactionMetadata::class.java
        )
    }

    override fun addUnnotarisedTransaction(transaction: SignedTransaction, metadata: FlowTransactionMetadata): Boolean {
        return addTransaction(transaction, metadata, TransactionStatus.IN_FLIGHT) {
            addTransactionRecoveryMetadata(transaction.id, metadata)
        }
    }

    private fun addTransactionRecoveryMetadata(txId: SecureHash, metadata: FlowTransactionMetadata): Boolean {

        val initiatorPartyInfo = DBRecoveryPartyInfo(0, metadata.initiator.toString())
        currentDBSession().save(initiatorPartyInfo)
        currentDBSession().flush()

//        val peersPartyInfo = metadata.peers?.map { DBRecoveryPartyInfo(0, it.toString()) } ?: emptyList()
//        peersPartyInfo.map { peerPartyInfo ->
//            currentDBSession().save(peerPartyInfo)
//        }
//        currentDBSession().flush()
        val peersPartyInfo = listOf(initiatorPartyInfo)

        val metadata = DBRecoveryTransactionMetadata(
                txId = txId.toString(),
                initiator = initiatorPartyInfo,
                peers = peersPartyInfo,
                statesToRecord = metadata.statesToRecord ?: StatesToRecord.ONLY_RELEVANT
        )
        currentDBSession().save(metadata)
        currentDBSession().flush()

        return true
    }

    private fun addTransaction(txId: SecureHash,
                               metadata: FlowTransactionMetadata? = null,
                               updateFn: (SecureHash) -> Boolean): Boolean {
        return database.transaction {
            txRecoveryMetadataStorage.locked {
                val cachedValue = TxRecoveryCacheValue(txId, metadata)
                val addedOrUpdated = addOrUpdate(txId, cachedValue) { k, _ -> updateFn(k) }
                if (addedOrUpdated) {
                    logger.debug { "Transaction recovery metadata for $txId has been recorded." }
                    true
                } else {
                    logger.debug { "Transaction recovery metadata for $txId is already recorded, so no need to re-record." }
                    false
                }
            }
        }
    }
}

