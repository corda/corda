package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.minutes
import net.corda.coretesting.internal.configureTestSSL
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.notary.common.BatchSignature
import net.corda.notary.experimental.raft.RaftConfig
import net.corda.notary.experimental.raft.RaftNotarySchemaV1
import net.corda.notary.experimental.raft.RaftUniquenessProvider
import net.corda.notary.jpa.JPANotaryConfiguration
import net.corda.notary.jpa.JPANotarySchemaV1
import net.corda.notary.jpa.JPAUniquenessProvider
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.generateStateRef
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.MockKeyManagementService
import net.corda.testing.node.makeTestIdentityService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.KeyPair
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
                JPAUniquenessProviderFactory(),
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
        There are 7 types of transaction to test:

                            A   B   C   D   E   F   G
        ================== === === === === === === ===
         Input states       0   0   0   1   1   1   1
         Reference states   0   1   1   0   0   1   1
         Time window        1   0   1   0   1   0   1
        ================== === === === === === === ===

        Here "0" indicates absence, and "1" – presence of components.
     */

    /* Group A: only time window */

    @Test(timeout=300_000)
    fun `rejects transaction before time window is valid`() {
        val firstTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.between(
                Clock.systemUTC().instant().plus(30.minutes),
                Clock.systemUTC().instant().plus(60.minutes))
        val result = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assert(result is UniquenessProvider.Result.Failure)
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(timeWindow, error.txTimeWindow)

        // Once time window behaviour has changed, we should add an additional test case here to check
        // that retry within time window still fails. We can't do that now because currently it will
        // succeed and that will result in the past time window case succeeding too.

        // Retry still fails after advancing past time window
        testClock.advanceBy(90.minutes)
        val result2 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assert(result2 is UniquenessProvider.Result.Failure)
        val error2 = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(timeWindow, error2.txTimeWindow)
    }

    @Test(timeout=300_000)
    fun `commits transaction within time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val result = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assert(result is UniquenessProvider.Result.Success)

        // Retry is successful whilst still within time window
        testClock.advanceBy(10.minutes)
        val result2 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assert(result2 is UniquenessProvider.Result.Success)

        // Retry is successful after time window has expired
        testClock.advanceBy(80.minutes)
        val result3 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assert(result3 is UniquenessProvider.Result.Success)
    }

    @Test(timeout=300_000)
    fun `rejects transaction after time window has expired`() {
        val firstTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        val result = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assert(result is UniquenessProvider.Result.Failure)
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(timeWindow, error.txTimeWindow)

        // Retry still fails at a later time
        testClock.advanceBy(10.minutes)
        val result2 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow).get()
        assert(result2 is UniquenessProvider.Result.Failure)
        val error2 = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(timeWindow, error2.txTimeWindow)
    }

    @Test(timeout=300_000)
    fun `time window only transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = SecureHash.randomSHA256()
        val secondTxId = SecureHash.randomSHA256()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        val validFuture1 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow)
        val validFuture2 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, timeWindow)
        val invalidFuture1 = uniquenessProvider.commit(
                emptyList(), secondTxId, identity, requestSignature, invalidTimeWindow)
        val invalidFuture2 = uniquenessProvider.commit(
                emptyList(), secondTxId, identity, requestSignature, invalidTimeWindow)

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assert(validFuture1.get() is UniquenessProvider.Result.Success)
        assert(validFuture2.get() is UniquenessProvider.Result.Success)
        assert(invalidFuture1.get() is UniquenessProvider.Result.Failure)
        assert(invalidFuture2.get() is UniquenessProvider.Result.Failure)
    }

    /* Group B: only reference states */

    @Test(timeout=300_000)
	fun `commits transaction with unused reference states`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        assert(result is UniquenessProvider.Result.Success)

        // Idempotency: can re-notarise successfully.
        val result2 = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        assert(result2 is UniquenessProvider.Result.Success)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with previously used reference states`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assert(result is UniquenessProvider.Result.Success)

        // Transaction referencing the spent sate fails.
        val secondTxId = SecureHash.randomSHA256()
        val result2 = uniquenessProvider.commit(emptyList(), secondTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        val error = (result2 as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        val conflictCause = error.consumedStates[referenceState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
        assertEquals(StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE, conflictCause.type)
    }

    @Test(timeout=300_000)
    fun `commits retry transaction when reference states were spent since initial transaction`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        assert(result is UniquenessProvider.Result.Success)

        // Spend reference state
        val secondTxId = SecureHash.randomSHA256()
        val result2 = uniquenessProvider.commit(
                listOf(referenceState), secondTxId, identity, requestSignature, references = emptyList())
                .get()
        assert(result2 is UniquenessProvider.Result.Success)

        // Retry referencing the now spent state still succeeds
        val result3 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
                .get()
        assert(result3 is UniquenessProvider.Result.Success)
    }

    @Test(timeout=300_000)
    fun `reference state only transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = SecureHash.randomSHA256()
        val secondTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val validFuture3 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
        val validFuture4 = uniquenessProvider.commit(
                emptyList(), firstTxId, identity, requestSignature, references = listOf(referenceState))
        val validFuture1 = uniquenessProvider.commit(
                emptyList(), secondTxId, identity, requestSignature, references = listOf(referenceState))
        val validFuture2 = uniquenessProvider.commit(
                emptyList(), secondTxId, identity, requestSignature, references = listOf(referenceState))

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assert(validFuture1.get() is UniquenessProvider.Result.Success)
        assert(validFuture2.get() is UniquenessProvider.Result.Success)
        assert(validFuture3.get() is UniquenessProvider.Result.Success)
        assert(validFuture4.get() is UniquenessProvider.Result.Success)
    }

    /* Group C: reference states & time window */

    @Test(timeout=300_000)
	fun `commits transaction with unused reference states and valid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        val result = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assert(result is UniquenessProvider.Result.Success)

        // The reference state gets consumed.
        val result2 = uniquenessProvider.commit(listOf(referenceState), SecureHash.randomSHA256(), identity, requestSignature, timeWindow)
                .get()
        assert(result2 is UniquenessProvider.Result.Success)

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        val result3 = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assert(result3 is UniquenessProvider.Result.Success)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with unused reference states and invalid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        val result = uniquenessProvider.commit(emptyList(), firstTxId, identity, requestSignature, invalidTimeWindow, references = listOf(referenceState))
                .get()
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(invalidTimeWindow, error.txTimeWindow)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with previously used reference states and valid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assert(result is UniquenessProvider.Result.Success)

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

    @Test(timeout=300_000)
	fun `rejects transaction with previously used reference states and invalid time window`() {
        val firstTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assert(result is UniquenessProvider.Result.Success)

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

    @Test(timeout=300_000)
	fun `commits transaction with unused inputs`() {
        val inputState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature).get()
        assert(result is UniquenessProvider.Result.Success)

        // Idempotency: can re-notarise successfully.
        val result2 = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature).get()
        assert(result2 is UniquenessProvider.Result.Success)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with previously used inputs`() {
        val inputState = generateStateRef()

        val inputs = listOf(inputState)
        val firstTxId = txID
        val result = uniquenessProvider.commit(inputs, firstTxId, identity, requestSignature).get()
        assert(result is UniquenessProvider.Result.Success)

        val secondTxId = SecureHash.randomSHA256()

        val response: UniquenessProvider.Result = uniquenessProvider.commit(inputs, secondTxId, identity, requestSignature).get()
        val error = (response as UniquenessProvider.Result.Failure).error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(firstTxId.sha256(), conflictCause.hashOfTransactionId)
    }

    @Test(timeout=300_000)
    fun `input state only transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = SecureHash.randomSHA256()
        val secondTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()

        val validFuture1 = uniquenessProvider.commit(
                listOf(inputState), firstTxId, identity, requestSignature)
        val validFuture2 = uniquenessProvider.commit(
                listOf(inputState), firstTxId, identity, requestSignature)
        val invalidFuture1 = uniquenessProvider.commit(
                listOf(inputState), secondTxId, identity, requestSignature)
        val invalidFuture2 = uniquenessProvider.commit(
                listOf(inputState), secondTxId, identity, requestSignature)

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assert(validFuture1.get() is UniquenessProvider.Result.Success)
        assert(validFuture2.get() is UniquenessProvider.Result.Success)
        assert(invalidFuture1.get() is UniquenessProvider.Result.Failure)
        assert(invalidFuture2.get() is UniquenessProvider.Result.Failure)
    }

    /* Group E: input states & time window */

    @Test(timeout=300_000)
	fun `commits transaction with unused inputs and valid time window`() {
        val inputState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        val result = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature, timeWindow).get()
        assert(result is UniquenessProvider.Result.Success)

        // Idempotency: can re-notarise successfully later.
        testClock.advanceBy(90.minutes)
        val result2 = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature, timeWindow).get()
        assert(result2 is UniquenessProvider.Result.Success)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with unused inputs and invalid time window`() {
        val inputState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        val result = uniquenessProvider.commit(listOf(inputState), txID, identity, requestSignature, invalidTimeWindow).get()
        val error = (result as UniquenessProvider.Result.Failure).error as NotaryError.TimeWindowInvalid
        assertEquals(invalidTimeWindow, error.txTimeWindow)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with previously used inputs and valid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val firstTxId = txID
        val result = uniquenessProvider.commit(inputs, firstTxId, identity, requestSignature).get()
        assert(result is UniquenessProvider.Result.Success)

        val secondTxId = SecureHash.randomSHA256()

        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val response: UniquenessProvider.Result = uniquenessProvider.commit(inputs, secondTxId, identity, requestSignature, timeWindow)
                .get()
        val error = (response as UniquenessProvider.Result.Failure).error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(firstTxId.sha256(), conflictCause.hashOfTransactionId)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with previously used inputs and invalid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val firstTxId = txID
        val result = uniquenessProvider.commit(inputs, firstTxId, identity, requestSignature).get()
        assert(result is UniquenessProvider.Result.Success)

        val secondTxId = SecureHash.randomSHA256()

        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        val response: UniquenessProvider.Result = uniquenessProvider.commit(inputs, secondTxId, identity, requestSignature, invalidTimeWindow)
                .get()
        val error = (response as UniquenessProvider.Result.Failure).error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(firstTxId.sha256(), conflictCause.hashOfTransactionId)
    }

    /* Group F: input & reference states */

    @Test(timeout=300_000)
	fun `commits transaction with unused input & reference states`() {
        val firstTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        val result = uniquenessProvider.commit(listOf(inputState), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assert(result is UniquenessProvider.Result.Success)

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        val result2 = uniquenessProvider.commit(listOf(inputState), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assert(result2 is UniquenessProvider.Result.Success)
    }

    @Test(timeout=300_000)
    fun `re-notarise after reference state is spent`() {
        val firstTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        val result = uniquenessProvider.commit(
                listOf(inputState), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        assert(result is UniquenessProvider.Result.Success)

        // Spend the reference state.
        val referenceSpend = uniquenessProvider.commit(
                listOf(referenceState),
                SecureHash.randomSHA256(),
                identity,
                requestSignature,
                timeWindow,
                emptyList()).get()
        assert(referenceSpend is UniquenessProvider.Result.Success)

        // Idempotency: can re-notarise successfully
        testClock.advanceBy(90.minutes)
        val result2 = uniquenessProvider.commit(
                listOf(inputState), firstTxId, identity, requestSignature, timeWindow, references = listOf(referenceState))
                .get()
        // Known failure - this should return success. Will be fixed in a future release.
        assert(result2 is UniquenessProvider.Result.Failure)
    }

    @Test(timeout=300_000)
	fun `rejects transaction with unused reference states and used input states`() {
        val firstTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(inputState), firstTxId, identity, requestSignature, references = emptyList()).get()
        assert(result is UniquenessProvider.Result.Success)

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

    @Test(timeout=300_000)
	fun `rejects transaction with used reference states and unused input states`() {
        val firstTxId = SecureHash.randomSHA256()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        val result = uniquenessProvider.commit(listOf(referenceState), firstTxId, identity, requestSignature, references = emptyList())
                .get()
        assert(result is UniquenessProvider.Result.Success)

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

    @Test(timeout=300_000)
    fun `input and reference state transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = SecureHash.randomSHA256()
        val secondTxId = SecureHash.randomSHA256()
        val referenceState = generateStateRef()

        // Ensure batch contains duplicates
        val validFuture1 = uniquenessProvider.commit(
                emptyList(), secondTxId, identity, requestSignature, references = listOf(referenceState))
        val validFuture2 = uniquenessProvider.commit(
                emptyList(), secondTxId, identity, requestSignature, references = listOf(referenceState))
        val validFuture3 = uniquenessProvider.commit(
                listOf(referenceState), firstTxId, identity, requestSignature)

        // Attempt to use the reference state after it has been consumed
        val validFuture4 = uniquenessProvider.commit(
                emptyList(), SecureHash.randomSHA256(), identity, requestSignature, references = listOf(referenceState))

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assert(validFuture1.get() is UniquenessProvider.Result.Success)
        assert(validFuture2.get() is UniquenessProvider.Result.Success)
        assert(validFuture3.get() is UniquenessProvider.Result.Success)
        assert(validFuture4.get() is UniquenessProvider.Result.Failure)
    }

    /* Group G: input, reference states and time window – covered by previous tests. */

    /* Transaction signing tests. */
    @Test(timeout=300_000)
	fun `signs transactions correctly`() {
        (1..10).map {
            val inputState1 = generateStateRef()
            val firstTxId = SecureHash.randomSHA256()
            val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
            Pair(firstTxId, uniquenessProvider.commit(listOf(inputState1), firstTxId, identity, requestSignature, timeWindow))
        }.forEach {
            val result = it.second.get()
            assert(result is UniquenessProvider.Result.Success)
            val signature = (result as UniquenessProvider.Result.Success).signature
            assert(signature.verify(it.first))
        }
    }
}

interface UniquenessProviderFactory {
    fun create(clock: Clock): UniquenessProvider
    fun cleanUp() {}
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
                RaftConfig(NetworkHostAndPort("localhost", raftNodePort), emptyList()),
                ::signSingle
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

fun signBatch(it: Iterable<SecureHash>): BatchSignature {
    val root = MerkleTree.getMerkleTree(it.map { it.sha256() })

    val signableMetadata = SignatureMetadata(4, Crypto.findSignatureScheme(pubKey).schemeNumberID)
    val signature = keyService.sign(SignableData(root.hash, signableMetadata), pubKey)
    return BatchSignature(signature, root)
}

class JPAUniquenessProviderFactory : UniquenessProviderFactory {
    private var database: CordaPersistence? = null
    private val notaryConfig = JPANotaryConfiguration(maxInputStates = 10)
    private val notaryWorkerName = CordaX500Name.parse("CN=NotaryWorker, O=Corda, L=London, C=GB")

    override fun create(clock: Clock): UniquenessProvider {
        database?.close()
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null }, NodeSchemaService(extraSchemas = setOf(JPANotarySchemaV1)))
        return JPAUniquenessProvider(
                clock,
                database!!,
                notaryConfig,
                notaryWorkerName,
                ::signBatch
        )
    }

    override fun cleanUp() {
        database?.close()
    }
}

var ourKeyPair: KeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
val keyService = MockKeyManagementService(makeTestIdentityService(), ourKeyPair)
val pubKey = keyService.freshKey()

fun signSingle(it: SecureHash, @Suppress("UNUSED_PARAMETER") notary: Party?) = keyService.sign(
        SignableData(
                txId = it,
                signatureMetadata = SignatureMetadata(
                        4,
                        Crypto.findSignatureScheme(pubKey).schemeNumberID
                )
        ), pubKey
)
