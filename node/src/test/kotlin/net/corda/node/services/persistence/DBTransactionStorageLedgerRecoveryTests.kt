package net.corda.node.services.persistence

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sign
import net.corda.core.flows.TransactionMetadata
import net.corda.core.flows.RecoveryTimeWindow
import net.corda.core.node.NodeInfo
import net.corda.core.node.StatesToRecord
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
        transactionRecovery.addUnnotarisedTransaction(newTransaction(), TransactionMetadata(ALICE.party.name, StatesToRecord.ALL_VISIBLE, setOf(BOB.party.name)), true)
        val timeWindow = RecoveryTimeWindow(fromTime = beforeFirstTxn,
                                            untilTime = beforeFirstTxn.plus(1, ChronoUnit.MINUTES))
        val results = transactionRecovery.queryForTransactions(timeWindow)
        assertEquals(1, results.size)

        val afterFirstTxn = now()
        transactionRecovery.addUnnotarisedTransaction(newTransaction(), TransactionMetadata(ALICE.party.name, StatesToRecord.ONLY_RELEVANT), true)
        assertEquals(2, transactionRecovery.queryForTransactions(timeWindow).size)
        assertEquals(1, transactionRecovery.queryForTransactions(RecoveryTimeWindow(fromTime = afterFirstTxn)).size)
    }

    @Test(timeout = 300_000)
    fun `query local ledger for transactions within timeWindow and excluding remoteTransactionIds`() {
        val transaction1 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction1, TransactionMetadata(ALICE.party.name, StatesToRecord.ALL_VISIBLE, setOf(BOB.party.name)), true)
        val transaction2 = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction2, TransactionMetadata(ALICE.party.name, StatesToRecord.ALL_VISIBLE, setOf(BOB.party.name)), true)
        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        val results = transactionRecovery.queryForTransactions(timeWindow, excludingTxnIds = setOf(transaction1.id))
        assertEquals(1, results.size)
    }

    @Test(timeout = 300_000)
    fun `query local ledger by distribution record type`() {
        val transaction1 = newTransaction()
        // sender txn
        transactionRecovery.addUnnotarisedTransaction(transaction1, TransactionMetadata(ALICE.party.name, StatesToRecord.ALL_VISIBLE, setOf(BOB.party.name)), true)
        val transaction2 = newTransaction()
        // receiver txn
        transactionRecovery.addUnnotarisedTransaction(transaction2, TransactionMetadata(BOB.party.name, StatesToRecord.ALL_VISIBLE, setOf(ALICE.party.name)), false)
        val timeWindow = RecoveryTimeWindow(fromTime = now().minus(1, ChronoUnit.DAYS))
        transactionRecovery.queryForTransactions(timeWindow, recordType = DistributionRecordType.SENDER).let {
            assertEquals(1, it.size)
            assertEquals(it[0].initiatorPartyId, ALICE.party.name.hashCode().toLong())
        }
        transactionRecovery.queryForTransactions(timeWindow, recordType = DistributionRecordType.RECEIVER).let {
            assertEquals(1, it.size)
            assertEquals(it[0].initiatorPartyId, BOB.party.name.hashCode().toLong())
        }
        val resultsAll = transactionRecovery.queryForTransactions(timeWindow, recordType = DistributionRecordType.ALL)
        assertEquals(2, resultsAll.size)
    }

    @Test(timeout = 300_000)
    fun `create un-notarised transaction with flow metadata and validate status in db`() {
        val transaction = newTransaction()
        transactionRecovery.addUnnotarisedTransaction(transaction, TransactionMetadata(ALICE.party.name, StatesToRecord.ALL_VISIBLE, setOf(BOB.party.name)), true)
        val txn = readTransactionFromDB(transaction.id)
        assertEquals(IN_FLIGHT, txn.status)
        readTransactionRecoveryDataFromDB(transaction.id).let {
            assertEquals(StatesToRecord.ALL_VISIBLE, it.statesToRecord)
            assertEquals(ALICE_NAME, partyInfoCache.getCordaX500NameByPartyId(it.initiatorPartyId))
            assertEquals(listOf(BOB_NAME), it.peerPartyIds.map { partyInfoCache.getCordaX500NameByPartyId(it) })
        }
    }

    @Test(timeout = 300_000)
    fun `finalize transaction with recovery metadata`() {
        val transaction = newTransaction(notarySig = false)
        transactionRecovery.finalizeTransaction(transaction,
                TransactionMetadata(ALICE_NAME), true)

        assertEquals(VERIFIED, readTransactionFromDB(transaction.id).status)
        readTransactionRecoveryDataFromDB(transaction.id).let {
            assertEquals(StatesToRecord.ONLY_RELEVANT, it.statesToRecord)
            assertEquals(ALICE_NAME, partyInfoCache.getCordaX500NameByPartyId(it.initiatorPartyId))
        }
    }

    @Test(timeout = 300_000)
    fun `remove un-notarised transaction and associated recovery metadata`() {
        val transaction = newTransaction(notarySig = false)
        transactionRecovery.addUnnotarisedTransaction(transaction, TransactionMetadata(ALICE.party.name), true)
        assertNull(transactionRecovery.getTransaction(transaction.id))
        assertEquals(IN_FLIGHT, readTransactionFromDB(transaction.id).status)

        assertEquals(true, transactionRecovery.removeUnnotarisedTransaction(transaction.id))
        assertFailsWith<AssertionError> { readTransactionFromDB(transaction.id).status }
        assertFailsWith<AssertionError> { readTransactionRecoveryDataFromDB(transaction.id) }
        assertNull(transactionRecovery.getTransactionInternal(transaction.id))
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

    private fun readTransactionRecoveryDataFromDB(id: SecureHash): DistributionRecord {
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${DBTransactionStorageLedgerRecovery.DBRecoveryTransactionMetadata::class.java.name} where tx_id = :transactionId",
                    DBTransactionStorageLedgerRecovery.DBRecoveryTransactionMetadata::class.java
            ).setParameter("transactionId", id.toString()).resultList.map { it }
        }
        assertEquals(1, fromDb.size)
        return fromDb[0].toTransactionRecoveryMetadata(MockCryptoService(emptyMap()))
    }

    private fun newTransactionRecovery(cacheSizeBytesOverride: Long? = null, clock: CordaClock = SimpleClock(Clock.systemUTC()),
                                       cryptoService: CryptoService = MockCryptoService(emptyMap())) {

        val networkMapCache = PersistentNetworkMapCache(TestingNamedCacheFactory(), database, InMemoryIdentityService(trustRoot = DEV_ROOT_CA.certificate))
        val alice = createNodeInfo(listOf(ALICE))
        val bob = createNodeInfo(listOf(BOB))
        networkMapCache.addOrUpdateNodes(listOf(alice, bob))
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
