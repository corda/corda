package net.corda.node.services.persistence

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sign
import net.corda.core.flows.DistributionList.ReceiverDistributionList
import net.corda.core.flows.DistributionList.SenderDistributionList
import net.corda.core.flows.ReceiverDistributionRecord
import net.corda.core.flows.SenderDistributionRecord
import net.corda.core.flows.TransactionMetadata
import net.corda.core.node.NodeInfo
import net.corda.core.node.StatesToRecord.ALL_VISIBLE
import net.corda.core.node.StatesToRecord.NONE
import net.corda.core.node.StatesToRecord.ONLY_RELEVANT
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.CordaClock
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.network.PersistentPartyInfoCache
import net.corda.node.services.persistence.DBTransactionStorage.TransactionStatus.IN_FLIGHT
import net.corda.node.services.persistence.DBTransactionStorage.TransactionStatus.VERIFIED
import net.corda.node.services.persistence.DBTransactionStorageLedgerRecovery.DBReceiverDistributionRecord
import net.corda.node.services.persistence.DBTransactionStorageLedgerRecovery.DBSenderDistributionRecord
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.dummyCommand
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.createWireTransaction
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.MockEncryptionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.KeyPair
import java.time.Clock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DBTransactionStorageLedgerRecoveryTests {
    private companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70)
        val BOB = TestIdentity(BOB_NAME, 80)
        val CHARLIE = TestIdentity(CHARLIE_NAME, 90)
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)

    private lateinit var database: CordaPersistence
    private lateinit var transactionRecovery: DBTransactionStorageLedgerRecovery
    private lateinit var partyInfoCache: PersistentPartyInfoCache

    private val encryptionService = MockEncryptionService()

    @Before
    fun setUp() {
        val dataSourceProps = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProps, DatabaseConfig(), { null }, { null })
        newTransactionRecovery()
    }

    @After
    fun cleanUp() {
        database.close()
    }

    fun now(): Instant {
        return transactionRecovery.clock.instant()
    }

    @Test(timeout = 300_000)
    fun `transaction without peers does not store recovery metadata in database`() {
        val senderTransaction = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(senderTransaction)
        transactionRecovery.addSenderTransactionRecoveryMetadata(senderTransaction.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, emptyMap())))
        assertEquals(IN_FLIGHT, readTransactionFromDB(senderTransaction.id).status)
        assertEquals(0, readSenderDistributionRecordFromDB(senderTransaction.id).size)
    }

    @Test(timeout = 300_000)
    fun `create un-notarised transaction with flow metadata and validate status in db`() {
        val senderTransaction = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(senderTransaction)
        transactionRecovery.addSenderTransactionRecoveryMetadata(senderTransaction.id,
                TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ALL_VISIBLE))))
        assertEquals(IN_FLIGHT, readTransactionFromDB(senderTransaction.id).status)
        readSenderDistributionRecordFromDB(senderTransaction.id).let {
            assertEquals(1, it.size)
            assertEquals(ALL_VISIBLE, it[0].senderStatesToRecord)
            assertEquals(BOB_NAME, partyInfoCache.getCordaX500NameByPartyId(it[0].peerPartyId))
        }

        val receiverTransaction = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(receiverTransaction)
        val encryptedDL = transactionRecovery.addSenderTransactionRecoveryMetadata(receiverTransaction.id,
                TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(BOB_NAME to ALL_VISIBLE))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(receiverTransaction.id, ALICE_NAME,
                TransactionMetadata(ALICE_NAME, ReceiverDistributionList(encryptedDL, ALL_VISIBLE)))
        assertEquals(IN_FLIGHT, readTransactionFromDB(receiverTransaction.id).status)
        readReceiverDistributionRecordFromDB(receiverTransaction.id).let { record ->
            val distList = transactionRecovery.decryptHashedDistributionList(record.encryptedDistributionList.bytes)
            assertEquals(ONLY_RELEVANT, distList.senderStatesToRecord)
            assertEquals(ALL_VISIBLE, distList.peerHashToStatesToRecord.values.first())
            assertEquals(ALICE_NAME, partyInfoCache.getCordaX500NameByPartyId(record.peerPartyId))
            assertEquals(setOf(BOB_NAME), distList.peerHashToStatesToRecord.map { (peer) -> partyInfoCache.getCordaX500NameByPartyId(peer) }.toSet() )
        }
    }

    @Test(timeout = 300_000)
    fun `finalize transaction with recovery metadata`() {
        val transaction = newTransaction(notarySig = false)
        transactionRecovery.finalizeTransaction(transaction)
        transactionRecovery.addSenderTransactionRecoveryMetadata(transaction.id,
                TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(CHARLIE_NAME to ALL_VISIBLE))))
        assertEquals(VERIFIED, readTransactionFromDB(transaction.id).status)
        readSenderDistributionRecordFromDB(transaction.id).apply {
            assertEquals(1, this.size)
            assertEquals(ONLY_RELEVANT, this[0].senderStatesToRecord)
            assertEquals(ALL_VISIBLE, this[0].receiverStatesToRecord)
        }
    }

    @Test(timeout = 300_000)
    fun `remove un-notarised transaction and associated recovery metadata`() {
        val senderTransaction = newTransaction(notarySig = false)
        transactionRecovery.addUnnotarisedTransaction(senderTransaction)
        val encryptedDL1 = transactionRecovery.addSenderTransactionRecoveryMetadata(senderTransaction.id,
                TransactionMetadata(ALICE.name, SenderDistributionList(ONLY_RELEVANT, mapOf(BOB.name to ONLY_RELEVANT, CHARLIE_NAME to ONLY_RELEVANT))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(senderTransaction.id, BOB.name,
                TransactionMetadata(ALICE.name, ReceiverDistributionList(encryptedDL1, ONLY_RELEVANT)))
        assertNull(transactionRecovery.getTransaction(senderTransaction.id))
        assertEquals(IN_FLIGHT, readTransactionFromDB(senderTransaction.id).status)

        assertEquals(true, transactionRecovery.removeUnnotarisedTransaction(senderTransaction.id))
        assertFailsWith<AssertionError> { readTransactionFromDB(senderTransaction.id).status }
        assertEquals(0, readSenderDistributionRecordFromDB(senderTransaction.id).size)
        assertNull(transactionRecovery.getTransactionWithStatus(senderTransaction.id))

        val receiverTransaction = newTransaction(notarySig = false)
        transactionRecovery.addUnnotarisedTransaction(receiverTransaction)
        val encryptedDL2 = transactionRecovery.addSenderTransactionRecoveryMetadata(receiverTransaction.id,
                TransactionMetadata(ALICE.name, SenderDistributionList(ONLY_RELEVANT, mapOf(BOB.name to ONLY_RELEVANT))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(receiverTransaction.id, BOB.name,
                TransactionMetadata(ALICE.name, ReceiverDistributionList(encryptedDL2, ONLY_RELEVANT)))
        assertNull(transactionRecovery.getTransaction(receiverTransaction.id))
        assertEquals(IN_FLIGHT, readTransactionFromDB(receiverTransaction.id).status)

        assertEquals(true, transactionRecovery.removeUnnotarisedTransaction(receiverTransaction.id))
        assertFailsWith<AssertionError> { readTransactionFromDB(receiverTransaction.id).status }
        assertFailsWith<AssertionError> { readReceiverDistributionRecordFromDB(receiverTransaction.id) }
        assertNull(transactionRecovery.getTransactionWithStatus(receiverTransaction.id))
    }

    @Test(timeout = 300_000)
    fun `test lightweight serialization and deserialization of hashed distribution list payload`() {
        val hashedDistList = HashedDistributionList(
                ALL_VISIBLE,
                mapOf(SecureHash.sha256(BOB.name.toString()) to NONE, SecureHash.sha256(CHARLIE_NAME.toString()) to ONLY_RELEVANT),
                HashedDistributionList.PublicHeader(now(), 1)
        )
        val roundtrip = HashedDistributionList.decrypt(hashedDistList.encrypt(encryptionService), encryptionService)
        assertThat(roundtrip).isEqualTo(hashedDistList)
    }

    private fun readTransactionFromDB(txId: SecureHash): DBTransactionStorage.DBTransaction {
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${DBTransactionStorage.DBTransaction::class.java.name} where tx_id = :transactionId",
                    DBTransactionStorage.DBTransaction::class.java
            ).setParameter("transactionId", txId.toString()).resultList
        }
        assertEquals(1, fromDb.size)
        return fromDb[0]
    }

    private fun readSenderDistributionRecordFromDB(txId: SecureHash? = null): List<SenderDistributionRecord> {
        return database.transaction {
            if (txId != null)
                session.createQuery(
                        "from ${DBSenderDistributionRecord::class.java.name} where transaction_id = :transactionId",
                        DBSenderDistributionRecord::class.java
                ).setParameter("transactionId", txId.toString()).resultList.map { it.toSenderDistributionRecord() }
            else
                session.createQuery(
                        "from ${DBSenderDistributionRecord::class.java.name}",
                        DBSenderDistributionRecord::class.java
                ).resultList.map { it.toSenderDistributionRecord() }
        }
    }

    private fun readReceiverDistributionRecordFromDB(txId: SecureHash): ReceiverDistributionRecord {
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${DBReceiverDistributionRecord::class.java.name} where transaction_id = :transactionId",
                    DBReceiverDistributionRecord::class.java
            ).setParameter("transactionId", txId.toString()).resultList
        }
        assertEquals(1, fromDb.size)
        return fromDb[0].toReceiverDistributionRecord()
    }

    private fun newTransactionRecovery(cacheSizeBytesOverride: Long? = null, clock: CordaClock = TestClock(Clock.systemUTC())) {
        val networkMapCache = PersistentNetworkMapCache(TestingNamedCacheFactory(), database, InMemoryIdentityService(trustRoot = DEV_ROOT_CA.certificate))
        val alice = createNodeInfo(listOf(ALICE))
        val bob = createNodeInfo(listOf(BOB))
        val charlie = createNodeInfo(listOf(CHARLIE))
        networkMapCache.addOrUpdateNodes(listOf(alice, bob, charlie))
        partyInfoCache = PersistentPartyInfoCache(networkMapCache, TestingNamedCacheFactory(), database)
        partyInfoCache.start()
        transactionRecovery = DBTransactionStorageLedgerRecovery(
                database,
                TestingNamedCacheFactory(cacheSizeBytesOverride ?: 1024),
                clock,
                encryptionService,
                partyInfoCache
        )
    }

    private var portCounter = 1000
    private fun createNodeInfo(identities: List<TestIdentity>,
                               address: NetworkHostAndPort = NetworkHostAndPort("localhost", portCounter++)): NodeInfo {
        return NodeInfo(
                addresses = listOf(address),
                legalIdentitiesAndCerts = identities.map { it.identity },
                platformVersion = 3,
                serial = 1
        )
    }

    private fun newTransaction(notarySig: Boolean = true): SignedTransaction {
        val wtx = createWireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = listOf(dummyCommand(ALICE.publicKey)),
                notary = DUMMY_NOTARY.party,
                timeWindow = null
        )
        return makeSigned(wtx, ALICE.keyPair, notarySig = notarySig)
    }

    private fun makeSigned(wtx: WireTransaction, vararg keys: KeyPair, notarySig: Boolean = true): SignedTransaction {
        val keySigs = keys.map { it.sign(SignableData(wtx.id, SignatureMetadata(1, Crypto.findSignatureScheme(it.public).schemeNumberID))) }
        val sigs = if (notarySig) {
            keySigs + notarySig(wtx.id)
        } else {
            keySigs
        }
        return SignedTransaction(wtx, sigs)
    }

    private fun notarySig(txId: SecureHash) =
            DUMMY_NOTARY.keyPair.sign(SignableData(txId, SignatureMetadata(1, Crypto.findSignatureScheme(DUMMY_NOTARY.publicKey).schemeNumberID)))
}

