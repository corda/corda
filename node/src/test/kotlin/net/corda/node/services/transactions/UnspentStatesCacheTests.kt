package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.notary.jpa.JPANotarySchemaV1
import net.corda.notary.jpa.JPAUniquenessProvider
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.generateStateRef
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnspentStatesCacheTests {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)

    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val txID = SecureHash.randomSHA256()
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)
    private lateinit var testClock: TestClock
    private var provider: JPAUniquenessProvider? = null

    private var database: CordaPersistence? = null

    @Before
    fun setUp() {
        testClock = TestClock(Clock.systemUTC())
    }

    @After
    fun tearDown() {
        provider?.stop()
        database?.close()
    }

    private fun createJPAUniquenessProvider(clock: Clock, unspentStatesCache: UnspentStatesCache?): JPAUniquenessProvider {
        database?.close()
        database = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(runMigration = true), { null }, { null }, NodeSchemaService(extraSchemas = setOf(JPANotarySchemaV1)))
        return JPAUniquenessProvider(MetricRegistry(), clock, database!!, unspentStatesCache = unspentStatesCache)
    }

    @Test
    fun `should take fast path for known unspent states`() {
        val unspentStatesCache = UnspentStatesCacheFactory().create()
        provider = createJPAUniquenessProvider(Clock.systemUTC(), unspentStatesCache)

        val inputState = generateStateRef()

        val inputs = listOf(inputState)
        assertFalse(unspentStatesCache.isAllUnspent(inputs))
        val future = provider!!.commit(inputs, txID, identity, requestSignature)
        val result = future.get()
        assertEquals(UniquenessProvider.Result.Success, result)
        assertFalse(unspentStatesCache.isAllUnspent(inputs))

        val secondInputs = listOf(StateRef(txID, 0))
        assertTrue(unspentStatesCache.isAllUnspent(secondInputs))

        val secondTxID = SecureHash.randomSHA256()
        val future2 = provider!!.commit(secondInputs, secondTxID, identity, requestSignature)
        val result2 = future2.get()
        assertEquals(UniquenessProvider.Result.Success, result2)
        assertFalse(unspentStatesCache.isAllUnspent(secondInputs))
        provider!!.stop()
    }

    @Test
    fun `should work with reference states`() {
        val referenceStates = listOf(generateStateRef())

        val inputs = listOf(generateStateRef())
        val secondTxId = SecureHash.randomSHA256()

        val unspentStatesCache = UnspentStatesCacheFactory().create()
        provider = createJPAUniquenessProvider(Clock.systemUTC(), unspentStatesCache)

        //Input states and reference states are not marked as unspent, either before or after the transaction is committed
        assertFalse(unspentStatesCache.isAllUnspent(inputs))
        assertFalse(unspentStatesCache.isAllUnspent(referenceStates))
        val result = provider!!.commit(inputs, txID, identity, requestSignature, references = referenceStates).get()
        assertEquals(UniquenessProvider.Result.Success, result)
        assertFalse(unspentStatesCache.isAllUnspent(inputs))
        assertFalse(unspentStatesCache.isAllUnspent(referenceStates))

        // Idempotency: can re-notarise successfully.
        val result2 = provider!!.commit(inputs, txID, identity, requestSignature, references = referenceStates).get()
        assertEquals(UniquenessProvider.Result.Success, result2)

        //Input states using the first txId are marked as unspent, while reference states are unaffected
        //Note that if we created reference states using the first txId, they would be marked as unspent
        val secondInputs = listOf(StateRef(txID, 0))
        assertTrue(unspentStatesCache.isAllUnspent(secondInputs))
        val result3 = provider!!.commit(secondInputs, secondTxId, identity, requestSignature, references = referenceStates).get()
        assertEquals(UniquenessProvider.Result.Success, result3)
        assertFalse(unspentStatesCache.isAllUnspent(secondInputs))
    }
}

class UnspentStatesCacheFactory {
    fun create(): UnspentStatesCache {
        return UnspentStatesCache(MetricRegistry())
    }
}