package net.corda.node.services.persistence

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sign
import net.corda.core.flows.DistributionList
import net.corda.core.flows.TransactionMetadata
import net.corda.core.flows.RecoveryTimeWindow
import net.corda.core.node.NodeInfo
import net.corda.core.node.StatesToRecord.ALL_VISIBLE
import net.corda.core.node.StatesToRecord.ONLY_RELEVANT
import net.corda.core.node.StatesToRecord.NONE
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
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.cryptoservice.CryptoService
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
import net.corda.testing.node.internal.MockCryptoService
import org.junit.After
import org.junit.Before
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
        transactionRecovery.addTransactionRecoveryMetadata(txn.id, TransactionMetadata(ALICE_NAME, DistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ONLY_RELEVANT))), ALICE_NAME)
        val timeWindow = RecoveryTimeWindow(fromTime = beforeFirstTxn,
                                            untilTime = beforeFirstTxn.plus(1, ChronoUnit.MINUTES))
        val results = transactionRecovery.querySenderDistributionRecords(timeWindow)
        assertEquals(1, results.size)

        val afterFirstTxn = now()
        val txn2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn2)
        transactionRecovery.addTransactionRecoveryMetadata(txn2.id, TransactionMetadata(ALICE_NAME, DistributionList(ONLY_RELEVANT, mapOf(CHARLIE_NAME to ONLY_RELEVANT))), ALICE_NAME)
        assertEquals(2, transactionRecovery.querySenderDistributionRecords(timeWindow).size)
        assertEquals(1, transactionRecovery.querySenderDistributionRecords(RecoveryTimeWindow(fromTime = afterFirstTxn)).size)
    }

    @Test(timeout = 300_000)
    fun `query local ledger for transactions within timeWindow and excluding remoteTransactionIds`() {
        val transaction1 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction1)
        transactionRecovery.addTransactionRecoveryMetadata(transaction1.id, TransactionMetadata(ALICE_NAME, DistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ONLY_RELEVANT))), ALICE_NAME)
        val transaction2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction2)
        transactionRecovery.addTransactionRecoveryMetadata(transaction2.id, TransactionMetadata(ALICE_NAME, DistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ONLY_RELEVANT))), ALICE_NAME)
        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        val results = transactionRecovery.querySenderDistributionRecords(timeWindow, excludingTxnIds = setOf(transaction1.id))
        assertEquals(1, results.size)
    }

    @Test(timeout = 300_000)
    fun `query local ledger by distribution record type`() {
        val transaction1 = newTransaction()
        // sender txn
        transactionRecovery.addUnnotarisedTransaction(transaction1)
        transactionRecovery.addTransactionRecoveryMetadata(transaction1.id, TransactionMetadata(ALICE_NAME, DistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ALL_VISIBLE))), ALICE_NAME)
        val transaction2 = newTransaction()
        // receiver txn
        transactionRecovery.addUnnotarisedTransaction(transaction2)
        transactionRecovery.addTransactionRecoveryMetadata(transaction2.id, TransactionMetadata(BOB_NAME, DistributionList(ONLY_RELEVANT, mapOf(ALICE_NAME to ALL_VISIBLE))), ALICE_NAME)
        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        transactionRecovery.queryDistributionRecords(timeWindow, recordType = DistributionRecordType.SENDER).let {
            assertEquals(1, it.size)
            assertEquals(BOB_NAME.hashCode().toLong(), (it[0] as SenderDistributionRecord).peerPartyId)
            assertEquals(ALL_VISIBLE, (it[0] as SenderDistributionRecord).statesToRecord)
        }
        transactionRecovery.queryDistributionRecords(timeWindow, recordType = DistributionRecordType.RECEIVER).let {
            assertEquals(1, it.size)
            assertEquals(BOB_NAME.hashCode().toLong(), (it[0] as ReceiverDistributionRecord).initiatorPartyId)
            assertEquals(ALL_VISIBLE, (it[0] as ReceiverDistributionRecord).statesToRecord)
        }
        val resultsAll = transactionRecovery.queryDistributionRecords(timeWindow, recordType = DistributionRecordType.ALL)
        assertEquals(2, resultsAll.size)
    }

    @Test(timeout = 300_000)
    fun `query for sender distribution records by peers`() {
        val txn1 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn1)
        transactionRecovery.addTransactionRecoveryMetadata(txn1.id, TransactionMetadata(ALICE_NAME, DistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ALL_VISIBLE))), ALICE_NAME)
        val txn2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn2)
        transactionRecovery.addTransactionRecoveryMetadata(txn2.id, TransactionMetadata(ALICE_NAME, DistributionList(ONLY_RELEVANT, mapOf(CHARLIE_NAME to ONLY_RELEVANT))), ALICE_NAME)
        val txn3 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn3)
        transactionRecovery.addTransactionRecoveryMetadata(txn3.id, TransactionMetadata(ALICE_NAME, DistributionList(ONLY_RELEVANT, mapOf(BOB_NAME to ONLY_RELEVANT, CHARLIE_NAME to ALL_VISIBLE))), ALICE_NAME)
        val txn4 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn4)
        transactionRecovery.addTransactionRecoveryMetadata(txn4.id, TransactionMetadata(BOB_NAME, DistributionList(ONLY_RELEVANT, mapOf(ALICE_NAME to ONLY_RELEVANT))), BOB_NAME)
        val txn5 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn5)
        transactionRecovery.addTransactionRecoveryMetadata(txn5.id, TransactionMetadata(CHARLIE_NAME), CHARLIE_NAME)
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
        transactionRecovery.addTransactionRecoveryMetadata(txn1.id, TransactionMetadata(ALICE_NAME,
                DistributionList(peersToStatesToRecord = mapOf(BOB_NAME to ALL_VISIBLE, CHARLIE_NAME to ALL_VISIBLE))), BOB_NAME)
        val txn2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn2)
        transactionRecovery.addTransactionRecoveryMetadata(txn2.id, TransactionMetadata(ALICE_NAME,
                DistributionList(peersToStatesToRecord = mapOf(BOB_NAME to ONLY_RELEVANT))), BOB_NAME)
        val txn3 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn3)
        transactionRecovery.addTransactionRecoveryMetadata(txn3.id, TransactionMetadata(ALICE_NAME,
                DistributionList(peersToStatesToRecord = mapOf(CHARLIE_NAME to NONE))), CHARLIE_NAME)
        val txn4 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn4)
        transactionRecovery.addTransactionRecoveryMetadata(txn4.id, TransactionMetadata(BOB_NAME,
                DistributionList(peersToStatesToRecord = mapOf(ALICE_NAME to ALL_VISIBLE))), ALICE_NAME)
        val txn5 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(txn5)
        transactionRecovery.addTransactionRecoveryMetadata(txn5.id, TransactionMetadata(CHARLIE_NAME,
                DistributionList(peersToStatesToRecord = mapOf(BOB_NAME to ONLY_RELEVANT))), BOB_NAME)

        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(ALICE_NAME)).let {
            assertEquals(3, it.size)
            assertEquals(it[0].statesToRecord, ALL_VISIBLE)
            assertEquals(it[1].statesToRecord, ONLY_RELEVANT)
            assertEquals(it[2].statesToRecord, NONE)
        }
        assertEquals(1, transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(BOB_NAME)).size)
        assertEquals(1, transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(CHARLIE_NAME)).size)
        assertEquals(2, transactionRecovery.queryReceiverDistributionRecords(timeWindow, initiators = setOf(BOB_NAME, CHARLIE_NAME)).size)
    }

    @Test(timeout = 300_000)
    fun `transaction without peers does not store recovery metadata in database`() {
        val senderTransaction = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(senderTransaction)
        transactionRecovery.addTransactionRecoveryMetadata(senderTransaction.id, TransactionMetadata(ALICE_NAME), ALICE_NAME)
        assertEquals(IN_FLIGHT, readTransactionFromDB(senderTransaction.id).status)
        assertEquals(0, readSenderDistributionRecordFromDB(senderTransaction.id).size)
    }

    @Test(timeout = 300_000)
    fun `create un-notarised transaction with flow metadata and validate status in db`() {
        val senderTransaction = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(senderTransaction)
        transactionRecovery.addTransactionRecoveryMetadata(senderTransaction.id,
                TransactionMetadata(ALICE_NAME, DistributionList(ALL_VISIBLE, mapOf(BOB_NAME to ALL_VISIBLE))), ALICE_NAME)
        assertEquals(IN_FLIGHT, readTransactionFromDB(senderTransaction.id).status)
        readSenderDistributionRecordFromDB(senderTransaction.id).let {
            assertEquals(1, it.size)
            assertEquals(ALL_VISIBLE, it[0].statesToRecord)
            assertEquals(BOB_NAME, partyInfoCache.getCordaX500NameByPartyId(it[0].peerPartyId))
        }

        val receiverTransaction = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(receiverTransaction)
        transactionRecovery.addTransactionRecoveryMetadata(receiverTransaction.id,
                TransactionMetadata(ALICE_NAME, DistributionList(ONLY_RELEVANT, mapOf(BOB_NAME to ALL_VISIBLE))), BOB_NAME)
        assertEquals(IN_FLIGHT, readTransactionFromDB(receiverTransaction.id).status)
        readReceiverDistributionRecordFromDB(receiverTransaction.id).let {
            assertEquals(ALL_VISIBLE, it.statesToRecord)
            assertEquals(ONLY_RELEVANT, it.senderStatesToRecord)
            assertEquals(ALICE_NAME, partyInfoCache.getCordaX500NameByPartyId(it.initiatorPartyId))
            assertEquals(setOf(BOB_NAME), it.peersToStatesToRecord.map { (peer, _) -> partyInfoCache.getCordaX500NameByPartyId(peer) }.toSet() )
        }
    }

    @Test(timeout = 300_000)
    fun `finalize transaction with recovery metadata`() {
        val transaction = newTransaction(notarySig = false)
        transactionRecovery.finalizeTransaction(transaction)
        transactionRecovery.addTransactionRecoveryMetadata(transaction.id,
                TransactionMetadata(ALICE_NAME, DistributionList(ONLY_RELEVANT, mapOf(CHARLIE_NAME to ALL_VISIBLE))), ALICE_NAME)
        assertEquals(VERIFIED, readTransactionFromDB(transaction.id).status)
        readSenderDistributionRecordFromDB(transaction.id).apply {
            assertEquals(1, this.size)
            assertEquals(ONLY_RELEVANT, this[0].statesToRecord)
        }
    }

    @Test(timeout = 300_000)
    fun `remove un-notarised transaction and associated recovery metadata`() {
        val senderTransaction = newTransaction(notarySig = false)
        transactionRecovery.addUnnotarisedTransaction(senderTransaction)
        transactionRecovery.addTransactionRecoveryMetadata(senderTransaction.id, TransactionMetadata(ALICE.name,
                DistributionList(peersToStatesToRecord = mapOf(BOB.name to ONLY_RELEVANT, CHARLIE_NAME to ONLY_RELEVANT))), BOB.name)
        assertNull(transactionRecovery.getTransaction(senderTransaction.id))
        assertEquals(IN_FLIGHT, readTransactionFromDB(senderTransaction.id).status)

        assertEquals(true, transactionRecovery.removeUnnotarisedTransaction(senderTransaction.id))
        assertFailsWith<AssertionError> { readTransactionFromDB(senderTransaction.id).status }
        assertEquals(0, readSenderDistributionRecordFromDB(senderTransaction.id).size)
        assertNull(transactionRecovery.getTransactionInternal(senderTransaction.id))

        val receiverTransaction = newTransaction(notarySig = false)
        transactionRecovery.addUnnotarisedTransaction(receiverTransaction)
        transactionRecovery.addTransactionRecoveryMetadata(receiverTransaction.id, TransactionMetadata(ALICE.name,
                DistributionList(peersToStatesToRecord = mapOf(BOB.name to ONLY_RELEVANT))), ALICE.name)
        assertNull(transactionRecovery.getTransaction(receiverTransaction.id))
        assertEquals(IN_FLIGHT, readTransactionFromDB(receiverTransaction.id).status)

        assertEquals(true, transactionRecovery.removeUnnotarisedTransaction(receiverTransaction.id))
        assertFailsWith<AssertionError> { readTransactionFromDB(receiverTransaction.id).status }
        assertFailsWith<AssertionError> { readReceiverDistributionRecordFromDB(receiverTransaction.id) }
        assertNull(transactionRecovery.getTransactionInternal(receiverTransaction.id))
    }

    private fun readTransactionFromDB(id: SecureHash): DBTransactionStorage.DBTransaction {
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${DBTransactionStorage.DBTransaction::class.java.name} where tx_id = :transactionId",
                    DBTransactionStorage.DBTransaction::class.java
            ).setParameter("transactionId", id.toString()).resultList.map { it }
        }
        assertEquals(1, fromDb.size)
        return fromDb[0]
    }

    private fun readSenderDistributionRecordFromDB(id: SecureHash? = null): List<SenderDistributionRecord> {
        return database.transaction {
            if (id != null)
                session.createQuery(
                        "from ${DBTransactionStorageLedgerRecovery.DBSenderDistributionRecord::class.java.name} where tx_id = :transactionId",
                        DBTransactionStorageLedgerRecovery.DBSenderDistributionRecord::class.java
                ).setParameter("transactionId", id.toString()).resultList.map { it.toSenderDistributionRecord() }
            else
                session.createQuery(
                        "from ${DBTransactionStorageLedgerRecovery.DBSenderDistributionRecord::class.java.name}",
                        DBTransactionStorageLedgerRecovery.DBSenderDistributionRecord::class.java
                ).resultList.map { it.toSenderDistributionRecord() }
        }
    }

    private fun readReceiverDistributionRecordFromDB(id: SecureHash): ReceiverDistributionRecord {
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${DBTransactionStorageLedgerRecovery.DBReceiverDistributionRecord::class.java.name} where tx_id = :transactionId",
                    DBTransactionStorageLedgerRecovery.DBReceiverDistributionRecord::class.java
            ).setParameter("transactionId", id.toString()).resultList.map { it }
        }
        assertEquals(1, fromDb.size)
        return fromDb[0].toReceiverDistributionRecord(MockCryptoService(emptyMap()))
    }

    private fun newTransactionRecovery(cacheSizeBytesOverride: Long? = null, clock: CordaClock = SimpleClock(Clock.systemUTC()),
                                       cryptoService: CryptoService = MockCryptoService(emptyMap())) {

        val networkMapCache = PersistentNetworkMapCache(TestingNamedCacheFactory(), database, InMemoryIdentityService(trustRoot = DEV_ROOT_CA.certificate))
        val alice = createNodeInfo(listOf(ALICE))
        val bob = createNodeInfo(listOf(BOB))
        val charlie = createNodeInfo(listOf(CHARLIE))
        networkMapCache.addOrUpdateNodes(listOf(alice, bob, charlie))
        partyInfoCache = PersistentPartyInfoCache(networkMapCache, TestingNamedCacheFactory(), database)
        partyInfoCache.start()
        transactionRecovery = DBTransactionStorageLedgerRecovery(database, TestingNamedCacheFactory(cacheSizeBytesOverride
                ?: 1024), clock, cryptoService, partyInfoCache)
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
