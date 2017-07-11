package net.corda.node.services.persistence

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.testing.NullPublicKey
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.LogHelper
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class DBTransactionStorageTests {
    lateinit var database: CordaPersistence
    lateinit var transactionStorage: DBTransactionStorage

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties())
        newTransactionStorage()
    }

    @After
    fun cleanUp() {
        database.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `empty store`() {
        database.transaction {
            assertThat(transactionStorage.getTransaction(newTransaction().id)).isNull()
        }
        database.transaction {
            assertThat(transactionStorage.transactions).isEmpty()
        }
        newTransactionStorage()
        database.transaction {
            assertThat(transactionStorage.transactions).isEmpty()
        }
    }

    @Test
    fun `one transaction`() {
        val transaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(transaction)
        }
        assertTransactionIsRetrievable(transaction)
        database.transaction {
            assertThat(transactionStorage.transactions).containsExactly(transaction)
        }
        newTransactionStorage()
        assertTransactionIsRetrievable(transaction)
        database.transaction {
            assertThat(transactionStorage.transactions).containsExactly(transaction)
        }
    }

    @Test
    fun `two transactions across restart`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
        }
        newTransactionStorage()
        database.transaction {
            transactionStorage.addTransaction(secondTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        database.transaction {
            assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
        }
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

        database.transaction {
            assertThat(transactionStorage.transactions).isEmpty()
        }
    }

    @Test
    fun `two transactions in same DB transaction scope`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
            transactionStorage.addTransaction(secondTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        database.transaction {
            assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
        }
    }

    @Test
    fun `updates are fired`() {
        val future = transactionStorage.updates.toFuture()
        val expected = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(expected)
        }
        val actual = future.get(1, TimeUnit.SECONDS)
        assertEquals(expected, actual)
    }

    private fun newTransactionStorage() {
        database.transaction {
            transactionStorage = DBTransactionStorage()
        }
    }

    private fun assertTransactionIsRetrievable(transaction: SignedTransaction) {
        database.transaction {
            assertThat(transactionStorage.getTransaction(transaction.id)).isEqualTo(transaction)
        }
    }

    private fun newTransaction(): SignedTransaction {
        val wtx = WireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = emptyList(),
                notary = DUMMY_NOTARY,
                signers = emptyList(),
                type = TransactionType.General,
                timeWindow = null
        )
        return SignedTransaction(wtx.serialized, listOf(DigitalSignature.WithKey(NullPublicKey, ByteArray(1))))
    }
}
