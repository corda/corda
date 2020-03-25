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
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.createWireTransaction
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.WriterAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import rx.plugins.RxJavaHooks
import java.io.StringWriter
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

    @Test
    fun `empty store`() {
        assertThat(transactionStorage.getTransaction(newTransaction().id)).isNull()
        assertThat(transactionStorage.transactions).isEmpty()
        newTransactionStorage()
        assertThat(transactionStorage.transactions).isEmpty()
    }

    @Test
    fun `one transaction`() {
        val transaction = newTransaction()
        transactionStorage.addTransaction(transaction)
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
        newTransactionStorage()
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
    }

    @Test
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

    @Test
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

    @Test
    fun `two transactions in same DB transaction scope`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        transactionStorage.addTransaction(firstTransaction)
        transactionStorage.addTransaction(secondTransaction)
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
    }

    @Test
    fun `transaction saved twice in same DB transaction scope`() {
        val firstTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
            transactionStorage.addTransaction(firstTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction)
    }

    @Test
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

    @Test
    fun `updates are fired`() {
        val future = transactionStorage.updates.toFuture()
        val expected = newTransaction()
        transactionStorage.addTransaction(expected)
        val actual = future.get(1, TimeUnit.SECONDS)
        assertEquals(expected, actual)
    }

    @Test
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

    @Test
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

    @Test
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
        val logMessages = collectLogsFrom {
            database.transaction {
                val result = transactionStorage.trackTransaction(signedTransaction.id)
                result.cancel(false)
            }
        }

        // Assert
        assertThat(logMessages).contains("trackTransaction is called with an already existing, open DB transaction. As a result, there might be transactions missing from the returned data feed, because of race conditions.")
    }

    private fun collectLogsFrom(statement: () -> Unit): String {
        // Create test appender
        val stringWriter = StringWriter()
        val appenderName = this::collectLogsFrom.name
        val appender: Appender = WriterAppender.createAppender(
                null,
                null,
                stringWriter,
                appenderName,
                false,
                true
        )
        appender.start()

        // Add test appender
        val context = LogManager.getContext(false) as LoggerContext
        val configuration = context.configuration
        configuration.addAppender(appender)
        configuration.loggers.values.forEach { it.addAppender(appender, null, null) }

        try {
            statement()
        } finally {
            // Remove test appender
            configuration.loggers.values.forEach { it.removeAppender(appenderName) }
            configuration.appenders.remove(appenderName)
            appender.stop()
        }

        return stringWriter.toString()
    }

    private fun newTransactionStorage(cacheSizeBytesOverride: Long? = null) {
        transactionStorage = DBTransactionStorage(database, TestingNamedCacheFactory(cacheSizeBytesOverride
                ?: 1024))
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
