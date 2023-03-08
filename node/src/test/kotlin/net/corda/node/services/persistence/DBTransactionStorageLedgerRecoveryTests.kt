package net.corda.node.services.persistence

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sign
import net.corda.core.flows.DistributionList.ReceiverDistributionList
import net.corda.core.flows.DistributionList.SenderDistributionList
import net.corda.core.flows.RecoveryTimeWindow
import net.corda.core.flows.TransactionMetadata
import net.corda.core.node.NodeInfo
import net.corda.core.node.StatesToRecord.ALL_VISIBLE
import net.corda.core.node.StatesToRecord.NONE
import net.corda.core.node.StatesToRecord.ONLY_RELEVANT
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.CordaClock
import net.corda.node.SimpleClock
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
import net.corda.testing.node.internal.MockEncryptionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.security.KeyPair
import java.time.Clock
import java.time.Instant.now
import java.time.temporal.ChronoUnit
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

    @Test(timeout = 300_000)
    fun `query local ledger for transactions with recovery peers within time window`() {
        val beforeFirstTxn = now()
        val txn = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn)
        transactionRecovery.addSenderTransactionRecoveryMetadata(txn.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ONLY_RELEVANT))))
        val timeWindow = RecoveryTimeWindow(fromTime = beforeFirstTxn,
                                            untilTime = beforeFirstTxn.plus(1, ChronoUnit.MINUTES))
        val results = transactionRecovery.querySenderDistributionRecords(timeWindow)
        assertEquals(1, results.size)

        val afterFirstTxn = now()
        val txn2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn2)
        transactionRecovery.addSenderTransactionRecoveryMetadata(txn2.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(CHARLIE_NAME to ONLY_RELEVANT))))
        assertEquals(2, transactionRecovery.querySenderDistributionRecords(timeWindow).size)
        assertEquals(1, transactionRecovery.querySenderDistributionRecords(RecoveryTimeWindow(fromTime = afterFirstTxn)).size)
    }

    @Test(timeout = 300_000)
    fun `query local ledger for transactions within timeWindow and excluding remoteTransactionIds`() {
        val transaction1 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction1)
        transactionRecovery.addSenderTransactionRecoveryMetadata(transaction1.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ONLY_RELEVANT))))
        val transaction2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction2)
        transactionRecovery.addSenderTransactionRecoveryMetadata(transaction2.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ONLY_RELEVANT))))
        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        val results = transactionRecovery.querySenderDistributionRecords(timeWindow, excludingTxnIds = setOf(transaction1.id))
        assertEquals(1, results.size)
        assertEquals(transaction2.id.toString(), results[0].txId)
    }

    @Test(timeout = 300_000)
    fun `query local ledger for transactions within timeWindow and for given peers`() {
        val transaction1 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction1)
        transactionRecovery.addSenderTransactionRecoveryMetadata(transaction1.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ONLY_RELEVANT))))
        val transaction2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction2)
        transactionRecovery.addSenderTransactionRecoveryMetadata(transaction2.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(CHARLIE_NAME to ONLY_RELEVANT))))
        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        val results = transactionRecovery.querySenderDistributionRecords(timeWindow, peers = setOf(CHARLIE_NAME))
        assertEquals(1, results.size)
        assertEquals(transaction2.id.toString(), results[0].txId)
    }

    @Test(timeout = 300_000)
    fun `query local ledger by distribution record type`() {
        val transaction1 = newTransaction()
        // sender txn
        transactionRecovery.addUnnotarisedTransaction(transaction1)
        transactionRecovery.addSenderTransactionRecoveryMetadata(transaction1.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ALL_VISIBLE))))
        val transaction2 = newTransaction()
        // receiver txn
        transactionRecovery.addUnnotarisedTransaction(transaction2)
        val encryptedDL = transactionRecovery.addSenderTransactionRecoveryMetadata(transaction2.id,
                TransactionMetadata(BOB_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(ALICE_NAME to ALL_VISIBLE))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(transaction2.id, BOB_NAME,
                TransactionMetadata(BOB_NAME, ReceiverDistributionList(encryptedDL, ALL_VISIBLE)))
        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        transactionRecovery.queryDistributionRecords(timeWindow, recordType = DistributionRecordType.SENDER).let {
            assertEquals(2, it.size)
            assertEquals(SecureHash.sha256(BOB_NAME.toString()).toString(), it.senderRecords[0].compositeKey.peerPartyId)
            assertEquals(ALL_VISIBLE, it.senderRecords[0].statesToRecord)
        }
        transactionRecovery.queryDistributionRecords(timeWindow, recordType = DistributionRecordType.RECEIVER).let {
            assertEquals(1, it.size)
            assertEquals(SecureHash.sha256(BOB_NAME.toString()).toString(), it.receiverRecords[0].compositeKey.peerPartyId)
            assertEquals(ALL_VISIBLE, (HashedDistributionList.decrypt(it.receiverRecords[0].distributionList, encryptionService)).peerHashToStatesToRecord.map { it.value }[0])
        }
        val resultsAll = transactionRecovery.queryDistributionRecords(timeWindow, recordType = DistributionRecordType.ALL)
        assertEquals(3, resultsAll.size)
    }

    @Test(timeout = 300_000)
    fun `query for sender distribution records by peers`() {
        val txn1 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn1)
        transactionRecovery.addSenderTransactionRecoveryMetadata(txn1.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ALL_VISIBLE))))
        val txn2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn2)
        transactionRecovery.addSenderTransactionRecoveryMetadata(txn2.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(CHARLIE_NAME to ONLY_RELEVANT))))
        val txn3 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn3)
        transactionRecovery.addSenderTransactionRecoveryMetadata(txn3.id, TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(BOB_NAME to ONLY_RELEVANT, CHARLIE_NAME to ALL_VISIBLE))))
        val txn4 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn4)
        transactionRecovery.addSenderTransactionRecoveryMetadata(txn4.id, TransactionMetadata(BOB_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(ALICE_NAME to ONLY_RELEVANT))))
        val txn5 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn5)
        transactionRecovery.addSenderTransactionRecoveryMetadata(txn5.id, TransactionMetadata(CHARLIE_NAME, SenderDistributionList(ONLY_RELEVANT, emptyMap())))
        assertEquals(5, readSenderDistributionRecordFromDB().size)

        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        transactionRecovery.querySenderDistributionRecords(timeWindow, peers = setOf(BOB_NAME)).let {
            assertEquals(2, it.size)
            assertEquals(it[0].statesToRecord, ALL_VISIBLE)
            assertEquals(it[1].statesToRecord, ONLY_RELEVANT)
        }
        assertEquals(1, transactionRecovery.querySenderDistributionRecords(timeWindow, peers = setOf(ALICE_NAME)).size)
        assertEquals(2, transactionRecovery.querySenderDistributionRecords(timeWindow, peers = setOf(CHARLIE_NAME)).size)
    }

    @Test(timeout = 300_000)
    fun `query for receiver distribution records by initiator`() {
        val txn1 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn1)
        val encryptedDL1 = transactionRecovery.addSenderTransactionRecoveryMetadata(txn1.id,
                TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(BOB_NAME to ALL_VISIBLE, CHARLIE_NAME to ALL_VISIBLE))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(txn1.id, ALICE_NAME,
                TransactionMetadata(ALICE_NAME, ReceiverDistributionList(encryptedDL1, ALL_VISIBLE)))
        val txn2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn2)
        val encryptedDL2 = transactionRecovery.addSenderTransactionRecoveryMetadata(txn2.id,
                TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(BOB_NAME to ONLY_RELEVANT))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(txn2.id, ALICE_NAME,
                TransactionMetadata(ALICE_NAME, ReceiverDistributionList(encryptedDL2, ONLY_RELEVANT)))
        val txn3 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn3)
        val encryptedDL3 = transactionRecovery.addSenderTransactionRecoveryMetadata(txn3.id,
                TransactionMetadata(ALICE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(CHARLIE_NAME to NONE))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(txn3.id, ALICE_NAME,
                TransactionMetadata(ALICE_NAME, ReceiverDistributionList(encryptedDL3, NONE)))
        val txn4 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn4)
        val encryptedDL4 = transactionRecovery.addSenderTransactionRecoveryMetadata(txn4.id,
                TransactionMetadata(BOB_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(ALICE_NAME to ALL_VISIBLE))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(txn4.id, BOB_NAME,
                TransactionMetadata(BOB_NAME, ReceiverDistributionList(encryptedDL4, ALL_VISIBLE)))
        val txn5 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn5)
        val encryptedDL5 = transactionRecovery.addSenderTransactionRecoveryMetadata(txn5.id,
                TransactionMetadata(CHARLIE_NAME, SenderDistributionList(ONLY_RELEVANT, mapOf(BOB_NAME to ONLY_RELEVANT))))
        transactionRecovery.addReceiverTransactionRecoveryMetadata(txn5.id, CHARLIE_NAME,
                TransactionMetadata(CHARLIE_NAME, ReceiverDistributionList(encryptedDL5, ONLY_RELEVANT)))

        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(ALICE_NAME)).let {
            assertEquals(3, it.size)
            assertEquals(HashedDistributionList.decrypt(it[0].distributionList, encryptionService).peerHashToStatesToRecord.map { it.value }[0], ALL_VISIBLE)
            assertEquals(HashedDistributionList.decrypt(it[1].distributionList, encryptionService).peerHashToStatesToRecord.map { it.value }[0], ONLY_RELEVANT)
            assertEquals(HashedDistributionList.decrypt(it[2].distributionList, encryptionService).peerHashToStatesToRecord.map { it.value }[0], NONE)
        }
        assertEquals(1, transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(BOB_NAME)).size)
        assertEquals(1, transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(CHARLIE_NAME)).size)
        assertEquals(2, transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(BOB_NAME, CHARLIE_NAME)).size)
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
            assertEquals(ALL_VISIBLE, it[0].statesToRecord)
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
            assertEquals(ALICE_NAME, partyInfoCache.getCordaX500NameByPartyId(record.initiatorPartyId))
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
            assertEquals(ALL_VISIBLE, this[0].statesToRecord)
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
        assertNull(transactionRecovery.getTransactionInternal(senderTransaction.id))

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
        assertNull(transactionRecovery.getTransactionInternal(receiverTransaction.id))
    }

    @Test(timeout = 300_000)
    @Ignore("TODO JDK17:Fixme datetime format issue")
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
                    "from ${DBTransactionStorage.DBTransaction::class.java.name} where txId = :transactionId",
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
                        "from ${DBSenderDistributionRecord::class.java.name} where txId = :transactionId",
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
                    "from ${DBReceiverDistributionRecord::class.java.name} where txId = :transactionId",
                    DBReceiverDistributionRecord::class.java
            ).setParameter("transactionId", txId.toString()).resultList
        }
        assertEquals(1, fromDb.size)
        return fromDb[0].toReceiverDistributionRecord()
    }

    private fun newTransactionRecovery(cacheSizeBytesOverride: Long? = null, clock: CordaClock = SimpleClock(Clock.systemUTC())) {
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

