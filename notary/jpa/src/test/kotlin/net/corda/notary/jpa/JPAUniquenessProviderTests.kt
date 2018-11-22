package net.corda.notary.jpa

import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.utilities.minutes
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.notary.jpa.JPAUniquenessProvider.Companion.decodeStateRef
import net.corda.notary.jpa.JPAUniquenessProvider.Companion.encodeStateRef
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.generateStateRef
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import kotlin.test.assertEquals

class JPAUniquenessProviderTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)
    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val txID = SecureHash.randomSHA256()
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)
    private val notaryConfig = JPANotaryConfiguration(maxInputStates = 10)


    private lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(JPAUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true), { null }, { null }, NodeSchemaService(extraSchemas = setOf(JPANotarySchemaV1)))
    }

    @After
    fun tearDown() {
        database.close()
        LogHelper.reset(JPAUniquenessProvider::class)
    }

    @Test
    fun `should successfully commit a transaction with unused inputs`() {
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val inputState = generateStateRef()

        val result = provider.commit(listOf(inputState), txID, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, result)
    }

    @Test
    fun `should report a conflict for a transaction with previously used inputs`() {
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val inputState = generateStateRef()

        val inputs = listOf(inputState)
        val firstTxId = txID
        val firstResult = provider.commit(inputs, firstTxId, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, firstResult)

        val secondTxId = SecureHash.randomSHA256()
        val secondResult = provider.commit(inputs, secondTxId, identity, requestSignature).get()
        val error = (secondResult as UniquenessProvider.Result.Failure).error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
    }

    @Test
    fun `serializes and deserializes state ref`() {
        val stateRef = generateStateRef()
        assertEquals(stateRef, decodeStateRef(encodeStateRef(stateRef)))
    }

    @Test
    fun `all conflicts are found with batching`() {
        val nrStates = notaryConfig.maxInputStates + notaryConfig.maxInputStates / 2
        val stateRefs = (1..nrStates).map { generateStateRef() }
        println(stateRefs.size)

        val firstTxId = SecureHash.randomSHA256()
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val firstResult = provider.commit(stateRefs, firstTxId, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, firstResult)

        val secondTxId = SecureHash.randomSHA256()
        val secondResult = provider.commit(stateRefs, secondTxId, identity, requestSignature).get()
        val error = (secondResult as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        assertEquals(nrStates, error.consumedStates.size)
    }

    @Test
    fun `handles reference states`() {
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val inputState1 = generateStateRef()
        val inputState2 = generateStateRef()
        val firstTxId = SecureHash.randomSHA256()
        val secondTxId = SecureHash.randomSHA256()

        // Conflict free transaction goes through.
        val result1 = provider.commit(listOf(inputState1), firstTxId, identity, requestSignature, references = listOf(inputState2)).get()
        assertEquals(UniquenessProvider.Result.Success, result1)

        // Referencing a spent state results in a conflict.
        val result2 = provider.commit(listOf(inputState2), secondTxId, identity, requestSignature, references = listOf(inputState1)).get()
        val error = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[inputState1]!!
        assertEquals(firstTxId.sha256(), conflictCause.hashOfTransactionId)
        assertEquals(StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE, conflictCause.type)

        // Re-notarisation works.
        val result3 = provider.commit(listOf(inputState1), firstTxId, identity, requestSignature, references = listOf(inputState2)).get()
        assertEquals(UniquenessProvider.Result.Success, result3)
    }

    @Test
    fun `handles transaction with reference states only`() {
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val inputState1 = generateStateRef()
        val firstTxId = SecureHash.randomSHA256()
        val secondTxId = SecureHash.randomSHA256()
        val thirdTxId = SecureHash.randomSHA256()

        // Conflict free transaction goes through.
        val result1 = provider.commit(emptyList(), firstTxId, identity, requestSignature, references = listOf(inputState1)).get()
        assertEquals(UniquenessProvider.Result.Success, result1)

        // Commit state 1.
        val result2 = provider.commit(listOf(inputState1), secondTxId, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, result2)

        // Re-notarisation works.
        val result3 = provider.commit(emptyList(), firstTxId, identity, requestSignature, references = listOf(inputState1)).get()
        assertEquals(UniquenessProvider.Result.Success, result3)

        // Transaction referencing the spent sate fails.
        val result4 = provider.commit(emptyList(), thirdTxId, identity, requestSignature, references = listOf(inputState1)).get()
        val error = (result4 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[inputState1]!!
        assertEquals(conflictCause.hashOfTransactionId, secondTxId.sha256())
        assertEquals(conflictCause.type, StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE)
    }

    @Test
    fun `rejects transaction with invalid time window`() {
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val inputState1 = generateStateRef()
        val firstTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.fromOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result = provider.commit(listOf(inputState1), firstTxId, identity, requestSignature, timeWindow).get()
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(timeWindow, error.txTimeWindow)
    }

    @Test
    fun `handles transaction with valid time window`() {
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val inputState1 = generateStateRef()
        val firstTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result = provider.commit(listOf(inputState1), firstTxId, identity, requestSignature, timeWindow).get()
        assertEquals(UniquenessProvider.Result.Success, result)
    }

    @Test
    fun `handles transaction with valid time window without inputs`() {
        val testClock = TestClock(Clock.systemUTC())
        val provider = JPAUniquenessProvider(testClock, database, notaryConfig)
        val firstTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result = provider.commit(emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Re-notarisation works outside the specified time window.
        testClock.advanceBy(90.minutes)
        val result2 = provider.commit(emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assertEquals(UniquenessProvider.Result.Success, result2)
    }
}
