package net.corda.node.services.persistence

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.*
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.toFuture
import net.corda.core.transactions.FilteredTransactionBuilder
import net.corda.core.transactions.NetworkParametersHash
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionTests
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.createWireTransaction
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DBTransactionStorageTests {
    private companion object {
        val ALICE_PUBKEY = TestIdentity(ALICE_NAME, 70).publicKey
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val DUMMY_KEY_1 = generateKeyPair()
        val DUMMY_KEY_2 = generateKeyPair()
        val DUMMY_CASH_ISSUER_KEY = entropyToKeyPair(BigInteger.valueOf(10))
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        val BOB = TestIdentity(BOB_NAME, 80).party
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val DUMMY_NOTARY_KEY get() = dummyNotary.keyPair
        val DUMMY_NETPARAMS = testNetworkParameters(minimumPlatformVersion = 4, notaries = listOf(NotaryInfo(DUMMY_NOTARY, true)))
        val outputState1 = TransactionState(DummyContract.SingleOwnerState(0, ALICE), DummyContract.PROGRAM_ID, DUMMY_NOTARY)
        val outputState2 = TransactionState(DummyContract.SingleOwnerState(1, ALICE), DummyContract.PROGRAM_ID, DUMMY_NOTARY)
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

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
    fun `filtered transaction`() {
        val transaction = createFilteredTransaction()
        transactionStorage.addTransaction(transaction)
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
        newTransactionStorage()
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
        val state = transactionStorage.resolveState(StateRef(transaction.id, 1))
        assertNotNull(state)
        val txState = state!!
        assertEquals(txState.deserialize(), outputState2)
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

    private fun createFilteredTransaction(): SignedTransaction {
        val tx = createWireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = listOf(outputState1, outputState2),
                commands = listOf(dummyCommand(DUMMY_KEY_1.public, DUMMY_KEY_2.public)),
                notary = DUMMY_NOTARY,
                timeWindow = null,
                networkParamHash = NetworkParametersHash(SecureHash.allOnesHash)
        )
        val filteredTx = FilteredTransactionBuilder(tx)
                .withOutputStates { _: TransactionState<ContractState>, id: Int -> id == 1 }
                .includeNetworkParameters(true)
                .build()
        return SignedTransaction(
                filteredTx,
                listOf(TransactionSignature(ByteArray(1), ALICE_PUBKEY, SignatureMetadata(1, Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID)))
        )
    }
}
