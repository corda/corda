package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
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
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.minutes
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.notary.experimental.raft.RaftConfig
import net.corda.notary.experimental.raft.RaftNotarySchemaV1
import net.corda.notary.experimental.raft.RaftUniquenessProvider
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.generateStateRef
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.configureTestSSL
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Clock
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class UniquenessProviderTests(
        private val uniquenessProviderFactory: UniquenessProviderFactory
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<UniquenessProviderFactory> = listOf(
                PersistentUniquenessProviderFactory(),
                RaftUniquenessProviderFactory()
        )
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)
    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val txID = SecureHash.randomSHA256()
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)
    private lateinit var testClock: TestClock
    private lateinit var uniquenessProvider: UniquenessProvider

    @Before
    fun setUp() {
        testClock = TestClock(Clock.systemUTC())
        uniquenessProvider = uniquenessProviderFactory.create(testClock)
        LogHelper.setLevel(uniquenessProvider::class)
    }

    @After
    fun tearDown() {
        uniquenessProviderFactory.cleanUp()
        LogHelper.reset(uniquenessProvider::class)
    }

    /*
        There are 6 types of transactions to test:

                            A   B   C   D   E   F   G
        ================== === === === === === === ===
         Input states       0   0   0   1   1   1   1
         Reference states   0   1   1   0   0   1   1
         Time window        1   0   1   0   1   0   1
        ================== === === === === === === ===

        Here "0" indicates absence, and "1" – presence of components.
     */

    /* Group A: only time window */

    @Test
    fun `commits transaction with valid time window`() {
        val inputState1 = generateStateRef()
        val firstTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result = uniquenessProvider.commit(listOf(inputState1), firstTxId, identity, requestSignature, timeWindow).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Idempotency: can re-notarise successfully later.
        testClock.advanceBy(90.minutes)
        val result2 = uniquenessProvider.commit(listOf(inputState1), firstTxId, identity, requestSignature, timeWindow).get()
        assertEquals(UniquenessProvider.Result.Success, result2)
    }

    @Test
    fun `rejects transaction with invalid time window`() {
        val inputState1 = generateStateRef()
        val firstTxId = SecureHash.randomSHA256()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        val result = uniquenessProvider.commit(listOf(inputState1), firstTxId, identity, requestSignature, invalidTimeWindow).get()
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(invalidTimeWindow, error.txTimeWindow)
    }

    /* Group B: only reference states */

    @Test
    fun `commits transaction with unused reference states`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Idempotency: can re-notarise successfully.
        val result2 = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        assertEquals(UniquenessProvider.Result.Success, result2)
    }

    @Test
    fun `rejects transaction with previously used reference states`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Transaction referencing the spent sate fails.
        val secondTxId = SecureHash.randomSHA256()
        val result2 = uniquenessProvider.commit(emptyList(), secondTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        val error = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[referenceState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
        assertEquals(StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE, conflictCause.type)
    }

    /* Group C: reference states & time window */

    @Test
    fun `commits transaction with unused reference states and valid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        val result = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // The reference state gets consumed.
        val result2 = uniquenessProvider.commit(listOf(referenceState), SecureHash.randomSHA256(), identity, requestSignature, timeWindow)
                .get()
        assertEquals(UniquenessProvider.Result.Success, result2)

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        val result3 = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assertEquals(UniquenessProvider.Result.Success, result3)
    }

    @Test
    fun `rejects transaction with unused reference states and invalid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        val result = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, invalidTimeWindow, references = listOf(referenceState))
                .get()
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(invalidTimeWindow, error.txTimeWindow)
    }

    @Test
    fun `rejects transaction with previously used reference states and valid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Transaction referencing the spent sate fails.
        val secondTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result2 = uniquenessProvider.commit(emptyList(), secondTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        val error = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[referenceState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
        assertEquals(StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE, conflictCause.type)
    }

    @Test
    fun `rejects transaction with previously used reference states and invalid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Transaction referencing the spent sate fails.
        val secondTxId = SecureHash.randomSHA256()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        val result2 = uniquenessProvider.commit(emptyList(), secondTxId, identity, requestSignature, invalidTimeWindow, references = listOf(referenceState))
                .get()
        val error = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[referenceState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
        assertEquals(StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE, conflictCause.type)
    }

    /* Group D: only input states */

    @Test
    fun `commits transaction with unused inputs`() {
        val inputState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Idempotency: can re-notarise successfully.
        val result2 = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, result2)
    }

    @Test
    fun `rejects transaction with previously used inputs`() {
        val inputState = generateStateRef()

        val inputs = listOf(inputState)
        val firstTxId = txID
        val result = uniquenessProvider.commit(inputs, firstTxId, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        val secondTxId = SecureHash.randomSHA256()

        val response: UniquenessProvider.Result = uniquenessProvider.commit(inputs, secondTxId, identity, requestSignature).get()
        val error = (response as UniquenessProvider.Result.Failure).error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(firstTxId.sha256(), conflictCause.hashOfTransactionId)
    }

    /* Group E: input states & time window */

    @Test
    fun `commits transaction with unused inputs and valid time window`() {
        val inputState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        val result = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature, timeWindow).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Idempotency: can re-notarise successfully later.
        testClock.advanceBy(90.minutes)
        val result2 = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature, timeWindow).get()
        assertEquals(UniquenessProvider.Result.Success, result2)
    }

    @Test
    fun `rejects transaction with unused inputs and invalid time window`() {
        val inputState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        val result = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature, invalidTimeWindow).get()
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(invalidTimeWindow, error.txTimeWindow)
    }

    @Test
    fun `rejects transaction with previously used inputs and valid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val firstTxId = txID
        val result = uniquenessProvider.commit(inputs, firstTxId, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        val secondTxId = SecureHash.randomSHA256()

        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val response: UniquenessProvider.Result = uniquenessProvider.commit(inputs, secondTxId, identity, requestSignature, timeWindow)
                .get()
        val error = (response as UniquenessProvider.Result.Failure).error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(firstTxId.sha256(), conflictCause.hashOfTransactionId)
    }

    @Test
    fun `rejects transaction with previously used inputs and invalid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val firstTxId = txID
        val result = uniquenessProvider.commit(inputs, firstTxId, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        val secondTxId = SecureHash.randomSHA256()

        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        val response: UniquenessProvider.Result = uniquenessProvider.commit(inputs, secondTxId, identity, requestSignature, invalidTimeWindow)
                .get()
        val error = (response as UniquenessProvider.Result.Failure).error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(firstTxId.sha256(), conflictCause.hashOfTransactionId)
    }

    /* Group F: input & reference states */

    @Test
    fun `commits transaction with unused input & reference states`() {
        val firstTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        val result = uniquenessProvider.commit(listOf(inputState), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        val result2 = uniquenessProvider.commit(listOf(inputState), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assertEquals(UniquenessProvider.Result.Success, result2)
    }

    @Test
    fun `rejects transaction with unused reference states and used input states`() {
        val firstTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(inputState), firstTxId, identity, requestSignature, references = emptyList()).get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Transaction referencing the spent sate fails.
        val secondTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result2 = uniquenessProvider.commit(listOf(inputState), secondTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        val error = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
        assertEquals(StateConsumptionDetails.ConsumedStateType.INPUT_STATE, conflictCause.type)
    }

    @Test
    fun `rejects transaction with used reference states and unused input states`() {
        val firstTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assertEquals(UniquenessProvider.Result.Success, result)

        // Transaction referencing the spent sate fails.
        val secondTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result2 = uniquenessProvider.commit(listOf(inputState), secondTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        val error = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[referenceState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
        assertEquals(StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE, conflictCause.type)
    }

    /* Group G: input, reference states and time window – covered by previous tests. */
}

interface UniquenessProviderFactory {
    fun create(clock: Clock): UniquenessProvider
    fun cleanUp() {}
}

class PersistentUniquenessProviderFactory : UniquenessProviderFactory {
    private var database: CordaPersistence? = null

    override fun create(clock: Clock): UniquenessProvider {
        database?.close()
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null }, NodeSchemaService(extraSchemas = setOf(NodeNotarySchemaV1)))
        return PersistentUniquenessProvider(clock, database!!, TestingNamedCacheFactory())
    }

    override fun cleanUp() {
        database?.close()
    }
}

class RaftUniquenessProviderFactory : UniquenessProviderFactory {
    private var database: CordaPersistence? = null
    private var provider: RaftUniquenessProvider? = null

    override fun create(clock: Clock): UniquenessProvider {
        database?.close()
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null }, NodeSchemaService(extraSchemas = setOf(RaftNotarySchemaV1)))

        val testSSL = configureTestSSL(CordaX500Name("Raft", "London", "GB"))
        val raftNodePort = 10987

        return RaftUniquenessProvider(
                null,
                testSSL,
                database!!,
                clock,
                MetricRegistry(),
                TestingNamedCacheFactory(),
                RaftConfig(NetworkHostAndPort("localhost", raftNodePort), emptyList())
        ).apply {
            start()
            provider = this
        }
    }

    override fun cleanUp() {
        provider?.stop()
        database?.close()
    }
}
