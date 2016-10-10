package com.r3corda.node.services.persistence

import com.google.common.primitives.Ints
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.utilities.LogHelper
import com.r3corda.node.services.transactions.PersistentUniquenessProvider
import com.r3corda.node.utilities.configureDatabase
import com.r3corda.node.utilities.databaseTransaction
import com.r3corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class DBTransactionStorageTests {
    lateinit var dataSource: Closeable
    lateinit var database: Database
    lateinit var transactionStorage: DBTransactionStorage

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        newTransactionStorage()
    }

    @After
    fun cleanUp() {
        dataSource.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `empty store`() {
        databaseTransaction(database) {
            assertThat(transactionStorage.getTransaction(newTransaction().id)).isNull()
        }
        databaseTransaction(database) {
            assertThat(transactionStorage.transactions).isEmpty()
        }
        newTransactionStorage()
        databaseTransaction(database) {
            assertThat(transactionStorage.transactions).isEmpty()
        }
    }

    @Test
    fun `one transaction`() {
        val transaction = newTransaction()
        databaseTransaction(database) {
            transactionStorage.addTransaction(transaction)
        }
        assertTransactionIsRetrievable(transaction)
        databaseTransaction(database) {
            assertThat(transactionStorage.transactions).containsExactly(transaction)
        }
        newTransactionStorage()
        assertTransactionIsRetrievable(transaction)
        databaseTransaction(database) {
            assertThat(transactionStorage.transactions).containsExactly(transaction)
        }
    }

    @Test
    fun `two transactions across restart`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        databaseTransaction(database) {
            transactionStorage.addTransaction(firstTransaction)
        }
        newTransactionStorage()
        databaseTransaction(database) {
            transactionStorage.addTransaction(secondTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        databaseTransaction(database) {
            assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
        }
    }

    @Test
    fun `two transactions in same DB transaction scope`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        databaseTransaction(database) {
            transactionStorage.addTransaction(firstTransaction)
            transactionStorage.addTransaction(secondTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        databaseTransaction(database) {
            assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
        }
    }

    @Test
    fun `updates are fired`() {
        val future = SettableFuture.create<SignedTransaction>()
        transactionStorage.updates.subscribe { tx -> future.set(tx) }
        val expected = newTransaction()
        databaseTransaction(database) {
            transactionStorage.addTransaction(expected)
        }
        val actual = future.get(1, TimeUnit.SECONDS)
        assertEquals(expected, actual)
    }

    private fun newTransactionStorage() {
        databaseTransaction(database) {
            transactionStorage = DBTransactionStorage()
        }
    }

    private fun assertTransactionIsRetrievable(transaction: SignedTransaction) {
        databaseTransaction(database) {
            assertThat(transactionStorage.getTransaction(transaction.id)).isEqualTo(transaction)
        }
    }

    private var txCount = 0
    private fun newTransaction() = SignedTransaction(
            SerializedBytes(Ints.toByteArray(++txCount)),
            listOf(DigitalSignature.WithKey(NullPublicKey, ByteArray(1))))
}