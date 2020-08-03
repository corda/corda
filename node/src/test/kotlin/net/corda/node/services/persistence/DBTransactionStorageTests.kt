package net.corda.node.services.persistence

import junit.framework.TestCase.assertTrue
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.node.CordaClock
import net.corda.node.MutableClock
import net.corda.node.SimpleClock
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.dummyCommand
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.createWireTransaction
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import rx.plugins.RxJavaHooks
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class DBTransactionStorageTests {
    private companion object {
        val ALICE_PUBKEY = TestIdentity(ALICE_NAME, 70).publicKey
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)

    private lateinit var database: CordaPersistence
    private lateinit var transactionStorage: DBTransactionStorage
    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        val dataSourceProps = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProps, DatabaseConfig(), { null }, { null })
        newTransactionStorage()
    }

    @After
    fun cleanUp() {
        database.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    private class TransactionClock(var timeNow: Instant,
                                   override var delegateClock: Clock = systemUTC()) : MutableClock(delegateClock) {
        override fun instant(): Instant = timeNow
    }

    @Test(timeout = 300_000)
    fun `create verified transaction and validate timestamp in db`() {
        val now = Instant.ofEpochSecond(111222333L)
        val transactionClock = TransactionClock(now)
        newTransactionStorage(clock = transactionClock)
        val transaction = newTransaction()
        transactionStorage.addTransaction(transaction)
        assertEquals(now, readTransactionTimestampFromDB(transaction.id))
    }

    @Test(timeout = 300_000)
    fun `create unverified transaction and validate timestamp in db`() {
        val now = Instant.ofEpochSecond(333444555L)
        val transactionClock = TransactionClock(now)
        newTransactionStorage(clock = transactionClock)
        val transaction = newTransaction()
        transactionStorage.addUnverifiedTransaction(transaction)
        assertEquals(now, readTransactionTimestampFromDB(transaction.id))
    }

    @Test(timeout = 300_000)
    fun `create unverified then verified transaction and validate timestamps in db`() {
        val unverifiedTime = Instant.ofEpochSecond(555666777L)
        val verifiedTime = Instant.ofEpochSecond(888999111L)
        val transactionClock = TransactionClock(unverifiedTime)
        newTransactionStorage(clock = transactionClock)
        val transaction = newTransaction()
        transactionStorage.addUnverifiedTransaction(transaction)
        assertEquals(unverifiedTime, readTransactionTimestampFromDB(transaction.id))
        transactionClock.timeNow = verifiedTime
        transactionStorage.addTransaction(transaction)
        assertEquals(verifiedTime, readTransactionTimestampFromDB(transaction.id))
    }

    @Test(timeout = 300_000)
    fun `check timestamp does not change when attempting to move transaction from verified to unverified`() {

        val verifiedTime = Instant.ofEpochSecond(555666222L)
        val differentTime = Instant.ofEpochSecond(888777666L)
        val transactionClock = TransactionClock(verifiedTime)

        newTransactionStorage(clock = transactionClock)
        val transaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(transaction)
        }
        assertEquals(verifiedTime, readTransactionTimestampFromDB(transaction.id))
        transactionClock.timeNow = differentTime
        database.transaction {
            transactionStorage.addUnverifiedTransaction(transaction)
        }
        assertTransactionIsRetrievable(transaction)
        assertEquals(verifiedTime, readTransactionTimestampFromDB(transaction.id))
    }

    @Test(timeout = 300_000)
    fun `check timestamp does not change when transaction saved twice in same DB transaction scope`() {
        val verifiedTime = Instant.ofEpochSecond(3333666222L)
        val differentTime = Instant.ofEpochSecond(111777666L)
        val transactionClock = TransactionClock(verifiedTime)
        newTransactionStorage(clock = transactionClock)
        val firstTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
            transactionClock.timeNow = differentTime
            transactionStorage.addTransaction(firstTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction)
        assertEquals(verifiedTime, readTransactionTimestampFromDB(firstTransaction.id))
    }

    @Test(timeout = 300_000)
    fun `check timestamp does not change when transaction saved twice in two DB transaction scopes`() {
        val verifiedTime = Instant.ofEpochSecond(11119999222L)
        val differentTime = Instant.ofEpochSecond(666333222L)
        val transactionClock = TransactionClock(verifiedTime)
        newTransactionStorage(clock = transactionClock)
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()

        transactionStorage.addTransaction(firstTransaction)
        assertEquals(verifiedTime, readTransactionTimestampFromDB(firstTransaction.id))
        transactionClock.timeNow = differentTime

        database.transaction {
            transactionStorage.addTransaction(secondTransaction)
            transactionStorage.addTransaction(firstTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
        assertEquals(verifiedTime, readTransactionTimestampFromDB(firstTransaction.id))
    }

    private fun readTransactionTimestampFromDB(id: SecureHash): Instant {
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${DBTransactionStorage.DBTransaction::class.java.name} where tx_id = :transactionId",
                    DBTransactionStorage.DBTransaction::class.java
            ).setParameter("transactionId", id.toString()).resultList.map { it }
        }
        assertEquals(1, fromDb.size)
        return fromDb[0].timestamp
    }

    @Test(timeout = 300_000)
    fun `empty store`() {
        assertThat(transactionStorage.getTransaction(newTransaction().id)).isNull()
        assertThat(transactionStorage.transactions).isEmpty()
        newTransactionStorage()
        assertThat(transactionStorage.transactions).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `one transaction`() {
        val transaction = newTransaction()
        transactionStorage.addTransaction(transaction)
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
        newTransactionStorage()
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
    }

    @Test(timeout = 300_000)
    fun `two transactions across restart`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        transactionStorage.addTransaction(firstTransaction)
        newTransactionStorage()
        transactionStorage.addTransaction(secondTransaction)
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
    }

    @Test(timeout = 300_000)
    fun `two transactions with rollback`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
            transactionStorage.addTransaction(secondTransaction)
            rollback()
        }

        assertThat(transactionStorage.transactions).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `two transactions in same DB transaction scope`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        transactionStorage.addTransaction(firstTransaction)
        transactionStorage.addTransaction(secondTransaction)
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
    }

    @Test(timeout = 300_000)
    fun `transaction saved twice in same DB transaction scope`() {
        val firstTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
            transactionStorage.addTransaction(firstTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction)
    }

    @Test(timeout = 300_000)
    fun `transaction saved twice in two DB transaction scopes`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()

        transactionStorage.addTransaction(firstTransaction)

        database.transaction {
            transactionStorage.addTransaction(secondTransaction)
            transactionStorage.addTransaction(firstTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
    }

    @Test(timeout = 300_000)
    fun `updates are fired`() {
        val future = transactionStorage.updates.toFuture()
        val expected = newTransaction()
        transactionStorage.addTransaction(expected)
        val actual = future.get(1, TimeUnit.SECONDS)
        assertEquals(expected, actual)
    }

    @Test(timeout = 300_000)
    fun `duplicates are detected when transaction is evicted from cache`() {
        newTransactionStorage(cacheSizeBytesOverride = 0)
        val transaction = newTransaction()
        database.transaction {
            val firstInserted = transactionStorage.addTransaction(transaction)
            val secondInserted = transactionStorage.addTransaction(transaction)
            require(firstInserted) { "We inserted a fresh transaction" }
            require(!secondInserted) { "Second time should be redundant" }
            println("$firstInserted $secondInserted")
        }
    }

    @Test(timeout = 300_000)
    fun `unverified transaction is correctly added in add transaction`() {
        val transaction = newTransaction()
        val added = database.transaction {
            transactionStorage.addUnverifiedTransaction(transaction)
            transactionStorage.addTransaction(transaction)
        }
        assertTransactionIsRetrievable(transaction)
        assertTrue(added)

        val secondTransaction = newTransaction()
        database.transaction {
            transactionStorage.addUnverifiedTransaction(secondTransaction)
        }

        val secondAdded = database.transaction {
            transactionStorage.addTransaction(secondTransaction)
        }

        assertTransactionIsRetrievable(secondTransaction)
        assertTrue(secondAdded)
    }

    @Test(timeout=300_000)
	fun `cannot move transaction from verified to unverified`() {
        val transaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(transaction)
            transactionStorage.addUnverifiedTransaction(transaction)
        }

        assertTransactionIsRetrievable(transaction)

        val secondTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(secondTransaction)
        }

        database.transaction {
            transactionStorage.addUnverifiedTransaction(secondTransaction)
        }
        assertTransactionIsRetrievable(secondTransaction)
    }

    @Suppress("UnstableApiUsage")
    @Test(timeout=300_000)
    fun `race condition - failure path`() {

        // Insert a sleep into trackTransaction
        RxJavaHooks.setOnObservableCreate {
            Thread.sleep(1_000)
            it
        }
        try {
            `race condition - ok path`()
        } finally {
            // Remove sleep so it does not affect other tests
            RxJavaHooks.setOnObservableCreate { it }
        }
    }

    @Test(timeout=300_000)
    fun `race condition - ok path`() {

        // Arrange

        val signedTransaction =  newTransaction()

        val threadCount = 2
        val finishedThreadsSemaphore = Semaphore(threadCount)
        finishedThreadsSemaphore.acquire(threadCount)

        // Act

        thread(name = "addTransaction") {
            transactionStorage.addTransaction(signedTransaction)
            finishedThreadsSemaphore.release()
        }

        var result: CordaFuture<SignedTransaction>? = null
        thread(name = "trackTransaction") {
            result =  transactionStorage.trackTransaction(signedTransaction.id)
            finishedThreadsSemaphore.release()
        }

        if (!finishedThreadsSemaphore.tryAcquire(threadCount, 1, TimeUnit.MINUTES)) {
            Assert.fail("Threads did not finish")
        }

        // Assert

        assertThat(result).isNotNull()
        assertThat(result?.get(20, TimeUnit.SECONDS)?.id).isEqualTo(signedTransaction.id)
    }

    @Test(timeout=300_000)
    fun `race condition - transaction warning`() {

        // Arrange
        val signedTransaction =  newTransaction()

        // Act
        val warning = database.transaction {
            val (result, warning) = transactionStorage.trackTransactionInternal(signedTransaction.id)
            result.cancel(false)
            warning
        }

        // Assert
        assertThat(warning).isEqualTo(DBTransactionStorage.TRANSACTION_ALREADY_IN_PROGRESS_WARNING)
    }

    private fun newTransactionStorage(cacheSizeBytesOverride: Long? = null, clock: CordaClock = SimpleClock(Clock.systemUTC())) {
        transactionStorage = DBTransactionStorage(database, TestingNamedCacheFactory(cacheSizeBytesOverride
                ?: 1024), clock)
    }

    private fun assertTransactionIsRetrievable(transaction: SignedTransaction) {
        assertThat(transactionStorage.getTransaction(transaction.id)).isEqualTo(transaction)
    }

    private fun newTransaction(): SignedTransaction {
        val wtx = createWireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = listOf(dummyCommand()),
                notary = DUMMY_NOTARY,
                timeWindow = null
        )
        return SignedTransaction(
                wtx,
                listOf(TransactionSignature(ByteArray(1), ALICE_PUBKEY, SignatureMetadata(1, Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID)))
        )
    }
}
