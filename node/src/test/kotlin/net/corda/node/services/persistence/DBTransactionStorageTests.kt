package net.corda.node.services.persistence

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.services.VaultService
import net.corda.core.schemas.MappedSchema
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.schemas.CashSchemaV1
import net.corda.schemas.SampleCashSchemaV2
import net.corda.schemas.SampleCashSchemaV3
import net.corda.testing.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import net.corda.testing.node.makeTestDatabaseProperties
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class DBTransactionStorageTests : TestDependencyInjectionBase() {
    lateinit var database: CordaPersistence
    lateinit var transactionStorage: DBTransactionStorage
    lateinit var services: MockServices
    val vault: VaultService get() = services.vaultService

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        val dataSourceProps = makeTestDataSourceProperties()

        val transactionSchema = MappedSchema(schemaFamily = javaClass, version = 1,
                mappedTypes = listOf(DBTransactionStorage.DBTransaction::class.java))

        val createSchemaService = { NodeSchemaService(setOf(VaultSchemaV1, CashSchemaV1, SampleCashSchemaV2, SampleCashSchemaV3, transactionSchema)) }

        database = configureDatabase(dataSourceProps, makeTestDatabaseProperties(), createSchemaService, ::makeTestIdentityService)

        database.transaction {

            services = object : MockServices(BOB_KEY) {
                override val vaultService: VaultService get() {
                    val vaultService = NodeVaultService(this)
                    hibernatePersister = HibernateObserver(vaultService.rawUpdates, database.hibernateConfig)
                    return vaultService
                }

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
        }
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
    fun `transaction saved twice in same DB transaction scope`() {
        val firstTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
            transactionStorage.addTransaction(firstTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
        database.transaction {
            assertThat(transactionStorage.transactions).containsOnly(firstTransaction)
        }
    }

    @Test
    fun `transaction saved twice in two DB transaction scopes`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        database.transaction {
            transactionStorage.addTransaction(firstTransaction)
        }

        database.transaction {
            transactionStorage.addTransaction(secondTransaction)
            transactionStorage.addTransaction(firstTransaction)
        }
        assertTransactionIsRetrievable(firstTransaction)
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
                commands = listOf(dummyCommand()),
                notary = DUMMY_NOTARY,
                timeWindow = null
        )
        return SignedTransaction(wtx, listOf(TransactionSignature(ByteArray(1), ALICE_PUBKEY, SignatureMetadata(1, Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID))))
    }
}
