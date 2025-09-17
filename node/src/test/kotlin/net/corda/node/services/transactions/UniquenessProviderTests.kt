package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.NotaryInstruction
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.randomHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails.ConsumedStateType
import net.corda.core.flows.StateConsumptionDetails.ConsumedStateType.INPUT_STATE
import net.corda.core.flows.StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.HashAgility
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.internal.notary.UniquenessProvider.Result
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.security.KeyPair
import java.time.Clock

@RunWith(Parameterized::class)
class UniquenessProviderTests(
        private val uniquenessProviderFactory: UniquenessProviderFactory,
        private val digestService: DigestService,
) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0} {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                    arrayOf(JPAUniquenessProviderFactory(DigestService.sha2_256), DigestService.sha2_256),
                    arrayOf(RaftUniquenessProviderFactory(), DigestService.sha2_256)
            )
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)

    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val txID = digestService.randomHash()
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)
    private lateinit var testClock: TestClock
    private lateinit var uniquenessProvider: UniquenessProvider

    @Before
    fun setUp() {
        testClock = TestClock(Clock.systemUTC())
        uniquenessProvider = uniquenessProviderFactory.create(testClock)
        LogHelper.setLevel(uniquenessProvider::class)
        LogHelper.setLevel("log4j.logger.org.hibernate.type")
        LogHelper.setLevel("log4j.logger.org.hibernate.SQL")
        HashAgility.init(txHashAlgoName = digestService.hashAlgorithm)
    }

    @After
    fun tearDown() {
        HashAgility.init()
        uniquenessProviderFactory.close()
        if (::uniquenessProvider.isInitialized) {
            LogHelper.reset(uniquenessProvider::class)
        }
        LogHelper.reset("log4j.logger.org.hibernate.type")
        LogHelper.reset("log4j.logger.org.hibernate.SQL")
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

    @Test(timeout = 300_000)
    fun `rejects transaction before time window is valid`() {
        val firstTxId = digestService.randomHash()
        val timeWindow = TimeWindow.between(
                Clock.systemUTC().instant().plus(30.minutes),
                Clock.systemUTC().instant().plus(60.minutes))
        expectInvalidTimeWindow(emptyList(), firstTxId, timeWindow)

        // Once time window behaviour has changed, we should add an additional test case here to check
        // that retry within time window still fails. We can't do that now because currently it will
        // succeed and that will result in the past time window case succeeding too.

        // Retry still fails after advancing past time window
        testClock.advanceBy(90.minutes)
        expectInvalidTimeWindow(emptyList(), firstTxId, timeWindow)
    }

    @Test(timeout = 300_000)
    fun `commits transaction within time window`() {
        val firstTxId = digestService.randomHash()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(emptyList(), firstTxId, timeWindow)

        // Retry is successful whilst still within time window
        testClock.advanceBy(10.minutes)
        expectCommitSuccess(emptyList(), firstTxId, timeWindow)

        // Retry is successful after time window has expired
        testClock.advanceBy(80.minutes)
        expectCommitSuccess(emptyList(), firstTxId, timeWindow)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction after time window has expired`() {
        val firstTxId = digestService.randomHash()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        expectInvalidTimeWindow(emptyList(), firstTxId, timeWindow)

        // Retry still fails at a later time
        testClock.advanceBy(10.minutes)
        expectInvalidTimeWindow(emptyList(), firstTxId, timeWindow)
    }

    @Test(timeout = 300_000)
    fun `time window only transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = digestService.randomHash()
        val secondTxId = digestService.randomHash()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        val validFuture1 = commit(emptyList(), firstTxId, timeWindow)
        val validFuture2 = commit(emptyList(), firstTxId, timeWindow)
        val invalidFuture1 = commit(emptyList(), secondTxId, invalidTimeWindow)
        val invalidFuture2 = commit(emptyList(), secondTxId, invalidTimeWindow)

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(invalidFuture1.get()).isInstanceOf(Result.Failure::class.java)
        assertThat(invalidFuture2.get()).isInstanceOf(Result.Failure::class.java)
    }

    /* Group B: only reference states */

    @Test(timeout = 300_000)
    fun `commits transaction with unused reference states`() {
        val firstTxId = digestService.randomHash()
        val referenceState = generateStateRef()

        expectCommitSuccess(emptyList(), firstTxId, references = listOf(referenceState))

        // Idempotency: can re-notarise successfully.
        expectCommitSuccess(emptyList(), firstTxId, references = listOf(referenceState))
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with previously used reference states`() {
        val firstTxId = digestService.randomHash()
        val referenceState = generateStateRef()

        expectCommitSuccess(listOf(referenceState), firstTxId, references = emptyList())

        // Transaction referencing the spent sate fails.
        val secondTxId = digestService.randomHash()
        val conflict = expectConflict(emptyList(), secondTxId, references = listOf(referenceState))
                .assertStateConsumed(referenceState, firstTxId, REFERENCE_INPUT_STATE)

        // Idempotency
        assertThat(expectConflict(emptyList(), secondTxId, references = listOf(referenceState))).isEqualTo(conflict)
    }

    @Test(timeout = 300_000)
    fun `commits retry transaction when reference states were spent since initial transaction`() {
        val firstTxId = digestService.randomHash()
        val referenceState = generateStateRef()

        expectCommitSuccess(emptyList(), firstTxId, references = listOf(referenceState))

        // Spend reference state
        val secondTxId = digestService.randomHash()
        expectCommitSuccess(listOf(referenceState), secondTxId, references = emptyList())

        // Retry referencing the now spent state still succeeds
        expectCommitSuccess(emptyList(), firstTxId, references = listOf(referenceState))
    }

    @Test(timeout = 300_000)
    fun `reference state only transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = digestService.randomHash()
        val secondTxId = digestService.randomHash()
        val referenceState = generateStateRef()

        val validFuture3 = commit(emptyList(), firstTxId, references = listOf(referenceState))
        val validFuture4 = commit(emptyList(), firstTxId, references = listOf(referenceState))
        val validFuture1 = commit(emptyList(), secondTxId, references = listOf(referenceState))
        val validFuture2 = commit(emptyList(), secondTxId, references = listOf(referenceState))

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture3.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture4.get()).isInstanceOf(Result.Success::class.java)
    }

    /* Group C: reference states & time window */

    @Test(timeout = 300_000)
    fun `commits transaction with unused reference states and valid time window`() {
        val firstTxId = digestService.randomHash()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(emptyList(), firstTxId, timeWindow, references = listOf(referenceState))

        // The reference state gets consumed.
        expectCommitSuccess(listOf(referenceState), digestService.randomHash(), timeWindow)

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        expectCommitSuccess(emptyList(), firstTxId, timeWindow, references = listOf(referenceState))
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with unused reference states and invalid time window`() {
        val firstTxId = digestService.randomHash()
        val referenceState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        expectInvalidTimeWindow(emptyList(), firstTxId, invalidTimeWindow, references = listOf(referenceState))

        // Idempotency
        expectInvalidTimeWindow(emptyList(), firstTxId, invalidTimeWindow, references = listOf(referenceState))
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with previously used reference states and valid time window`() {
        val firstTxId = digestService.randomHash()
        val referenceState = generateStateRef()

        expectCommitSuccess(listOf(referenceState), firstTxId, references = emptyList())

        // Transaction referencing the spent sate fails.
        val secondTxId = digestService.randomHash()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val conflict = expectConflict(emptyList(), secondTxId, timeWindow, references = listOf(referenceState))
                .assertStateConsumed(referenceState, firstTxId, REFERENCE_INPUT_STATE)

        // Idempotency
        assertThat(expectConflict(emptyList(), secondTxId, timeWindow, references = listOf(referenceState))).isEqualTo(conflict)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with previously used reference states and invalid time window`() {
        val firstTxId = digestService.randomHash()
        val referenceState = generateStateRef()

        expectCommitSuccess(listOf(referenceState), firstTxId, references = emptyList())

        // Transaction referencing the spent state fails.
        val secondTxId = digestService.randomHash()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        expectConflict(emptyList(), secondTxId, invalidTimeWindow, references = listOf(referenceState))
                .assertStateConsumed(referenceState, firstTxId, REFERENCE_INPUT_STATE)
    }

    /* Group D: only input states */

    @Test(timeout = 300_000)
    fun `commits transaction with unused inputs`() {
        val inputState = generateStateRef()

        expectCommitSuccess(listOf(inputState), txID)

        // Idempotency: can re-notarise successfully.
        expectCommitSuccess(listOf(inputState), txID)
    }

    @Test(timeout = 300_000)
    fun `commits transaction with multiple inputs (less than 10)`() {
        val inputs = Array(6) { generateStateRef() }.asList()

        expectCommitSuccess(inputs, txID)

        // Idempotency: can re-notarise successfully.
        expectCommitSuccess(inputs, txID)
    }

    @Test(timeout = 300_000)
    fun `commits transaction with multiple inputs (more than 10)`() {
        val inputs = Array(16) { generateStateRef() }.asList()

        expectCommitSuccess(inputs, txID)

        // Idempotency: can re-notarise successfully.
        expectCommitSuccess(inputs, txID)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with one of the inputs previously used`() {
        val unusedInput = generateStateRef()
        val usedInput = generateStateRef()
        expectCommitSuccess(listOf(usedInput), txID)

        val secondTxId = digestService.randomHash()
        val conflict = expectConflict(listOf(unusedInput, usedInput), secondTxId)
                .assertStateConsumed(usedInput, txID, INPUT_STATE)
        assertThat(conflict.consumedStates).doesNotContainKey(unusedInput)

        // Idempotency
        assertThat(expectConflict(listOf(unusedInput, usedInput), secondTxId)).isEqualTo(conflict)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with all used inputs`() {
        // The two states consumed by different transactions.
        val secondTxId = digestService.randomHash()
        val thirdTxId = digestService.randomHash()
        val input1 = generateStateRef()
        val input2 = generateStateRef()

        expectCommitSuccess(listOf(input1), txID)
        expectCommitSuccess(listOf(input2), secondTxId)

        val conflict = expectConflict(listOf(input1, input2), thirdTxId)
                .assertStateConsumed(input1, txID, INPUT_STATE)
                .assertStateConsumed(input2, secondTxId, INPUT_STATE)

        // Idempotency
        assertThat(expectConflict(listOf(input1, input2), thirdTxId)).isEqualTo(conflict)
    }

    @Test(timeout = 300_000)
    fun `input state only transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = digestService.randomHash()
        val secondTxId = digestService.randomHash()
        val inputState = generateStateRef()

        val validFuture1 = commit(listOf(inputState), firstTxId)
        val validFuture2 = commit(listOf(inputState), firstTxId)
        val invalidFuture1 = commit(listOf(inputState), secondTxId)
        val invalidFuture2 = commit(listOf(inputState), secondTxId)

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(invalidFuture1.get()).isInstanceOf(Result.Failure::class.java)
        assertThat(invalidFuture2.get()).isInstanceOf(Result.Failure::class.java)
    }

    /* Group E: input states & time window */

    @Test(timeout = 300_000)
    fun `commits transaction with unused inputs and valid time window`() {
        val inputState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(listOf(inputState), txID, timeWindow)

        // Idempotency: can re-notarise successfully later.
        testClock.advanceBy(90.minutes)
        expectCommitSuccess(listOf(inputState), txID, timeWindow)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with unused inputs and invalid time window, and input isn't used`() {
        val inputState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        expectInvalidTimeWindow(listOf(inputState), txID, invalidTimeWindow)

        // Make sure the input state wasn't spent.
        expectCommitSuccess(listOf(inputState), digestService.randomHash())
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with previously used inputs and valid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val firstTxId = txID
        expectCommitSuccess(inputs, firstTxId)

        val secondTxId = digestService.randomHash()

        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val conflict = expectConflict(inputs, secondTxId, timeWindow)
                .assertStateConsumed(inputState, firstTxId, INPUT_STATE)

        // Idempotency
        assertThat(expectConflict(inputs, secondTxId, timeWindow)).isEqualTo(conflict)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with previously used inputs and invalid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val firstTxId = txID
        expectCommitSuccess(inputs, firstTxId)

        val secondTxId = digestService.randomHash()

        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        expectConflict(inputs, secondTxId, invalidTimeWindow)
                .assertStateConsumed(inputState, firstTxId, INPUT_STATE)
    }

    /* Group F: input & reference states */

    @Test(timeout = 300_000)
    fun `commits transaction with unused input & reference states`() {
        val firstTxId = digestService.randomHash()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(listOf(inputState), firstTxId, timeWindow, references = listOf(referenceState))

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        expectCommitSuccess(listOf(inputState), firstTxId, timeWindow, references = listOf(referenceState))
    }

    @Test(timeout = 300_000)
    fun `re-notarise after reference state is spent`() {
        val firstTxId = digestService.randomHash()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(listOf(inputState), firstTxId, timeWindow, references = listOf(referenceState))

        // Spend the reference state.
        expectCommitSuccess(listOf(referenceState), digestService.randomHash(), timeWindow)

        // Idempotency: can re-notarise successfully
        testClock.advanceBy(90.minutes)
        val result = commit(listOf(inputState), firstTxId, timeWindow, references = listOf(referenceState)).get()
        // Known failure - this should return success. Will be fixed in a future release.
        assertThat(result).isInstanceOf(Result.Failure::class.java)

        // Idempotency
        assertThat(commit(listOf(inputState), firstTxId, timeWindow, listOf(referenceState)).get()).isEqualTo(result)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with unused reference states and used input states`() {
        val firstTxId = digestService.randomHash()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        expectCommitSuccess(listOf(inputState), firstTxId, references = emptyList())

        // Transaction referencing the spent sate fails.
        val secondTxId = digestService.randomHash()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val conflict = expectConflict(listOf(inputState), secondTxId, timeWindow, references = listOf(referenceState))
                .assertStateConsumed(inputState, firstTxId, INPUT_STATE)

        // Idempotency
        assertThat(expectConflict(listOf(inputState), secondTxId, timeWindow, listOf(referenceState))).isEqualTo(conflict)
    }

    @Test(timeout = 300_000)
    fun `rejects transaction with used reference states and unused input states`() {
        val firstTxId = digestService.randomHash()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        expectCommitSuccess(listOf(referenceState), firstTxId, references = emptyList())

        // Transaction referencing the spent state fails.
        val secondTxId = digestService.randomHash()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val conflict = expectConflict(listOf(inputState), secondTxId, timeWindow, references = listOf(referenceState))
                .assertStateConsumed(referenceState, firstTxId, REFERENCE_INPUT_STATE)

        // Idempotency
        assertThat(expectConflict(listOf(inputState), secondTxId, timeWindow, listOf(referenceState))).isEqualTo(conflict)
    }

    @Test(timeout = 300_000)
    fun `input and reference state transactions are processed correctly when duplicate requests occur in succession`() {
        val firstTxId = digestService.randomHash()
        val secondTxId = digestService.randomHash()
        val referenceState = generateStateRef()

        // Ensure batch contains duplicates
        val validFuture1 = commit(emptyList(), secondTxId, references = listOf(referenceState))
        val validFuture2 = commit(emptyList(), secondTxId, references = listOf(referenceState))
        val validFuture3 = commit(listOf(referenceState), firstTxId)

        // Attempt to use the reference state after it has been consumed
        val validFuture4 = commit(emptyList(), digestService.randomHash(), references = listOf(referenceState))

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture3.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture4.get()).isInstanceOf(Result.Failure::class.java)
    }

    /* Group G: input, reference states and time window – covered by previous tests. */

    @Test(timeout = 300_000)
    fun `by default notary instructions are not supported`() {
        assumeThat(uniquenessProviderFactory.isNotaryInstructionSupported).isFalse

        class FakeNotaryInstruction : NotaryInstruction

        assertThat(uniquenessProvider.isNotaryInstructionsValid(listOf())).isTrue
        assertThat(uniquenessProvider.isNotaryInstructionsValid(listOf(FakeNotaryInstruction()))).isFalse
    }

    @Suppress("LongParameterList")
    private fun commit(
            states: List<StateRef>,
            txId: SecureHash,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList(),
            notaryInstructions: List<NotaryInstruction> = emptyList(),
            transactionSignatures: List<TransactionSignature> = emptyList()
    ): CordaFuture<Result> {
        return uniquenessProvider.commit(
                states,
                txId,
                identity,
                requestSignature,
                timeWindow,
                references,
                notaryInstructions,
                transactionSignatures
        )
    }

    private fun expectCommitSuccess(
            states: List<StateRef>,
            txId: SecureHash,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList(),
            notaryInstructions: List<NotaryInstruction> = emptyList()
    ) {
        val result = commit(states, txId, timeWindow, references, notaryInstructions).get()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        result as Result.Success
        result.signature.verify(txId)
    }

    private fun expectCommitFailure(
            states: List<StateRef>,
            txId: SecureHash,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList(),
            notaryInstructions: List<NotaryInstruction> = emptyList()
    ): NotaryError {
        val result = commit(states, txId, timeWindow, references, notaryInstructions).get()
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        return (result as Result.Failure).error
    }

    private fun expectInvalidTimeWindow(
            states: List<StateRef>,
            txId: SecureHash,
            timeWindow: TimeWindow,
            references: List<StateRef> = emptyList(),
    ) {
        val notaryError = expectCommitFailure(states, txId, timeWindow, references)
        assertThat(notaryError).isInstanceOf(NotaryError.TimeWindowInvalid::class.java)
        assertThat((notaryError as NotaryError.TimeWindowInvalid).txTimeWindow).isEqualTo(timeWindow)
    }

    private fun expectConflict(
            states: List<StateRef>,
            txId: SecureHash,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList(),
    ): NotaryError.Conflict {
        val notaryError = expectCommitFailure(states, txId, timeWindow, references)
        assertThat(notaryError).isInstanceOf(NotaryError.Conflict::class.java)
        val conflict = notaryError as NotaryError.Conflict
        assertThat(conflict.txId).isEqualTo(txId)
        return conflict
    }

    private fun NotaryError.Conflict.assertStateConsumed(
            stateRef: StateRef,
            consumingTxId: SecureHash,
            expectedType: ConsumedStateType,
    ): NotaryError.Conflict {
        assertThat(consumedStates).containsKey(stateRef)
        val consumptionDetails = consumedStates.getValue(stateRef)
        assertThat(consumptionDetails.type).isEqualTo(expectedType)
        assertThat(consumptionDetails.hashOfTransactionId).isEqualTo(consumingTxId.reHash())
        return this
    }
}

interface UniquenessProviderFactory : AutoCloseable {
    val isNotaryInstructionSupported: Boolean
        get() = false
    fun create(clock: Clock): UniquenessProvider
}

class RaftUniquenessProviderFactory : UniquenessProviderFactory {
    private var database: CordaPersistence? = null
    private var provider: RaftUniquenessProvider? = null

    override fun create(clock: Clock): UniquenessProvider {
        database?.close()
        database = configureDatabase(
                makeTestDataSourceProperties(),
                DatabaseConfig(),
                { null },
                { null },
                NodeSchemaService(extraSchemas = setOf(RaftNotarySchemaV1))
        )

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

    override fun close() {
        provider?.stop()
        database?.close()
    }

    override fun toString(): String = "Raft"
}

class JPAUniquenessProviderFactory(private val digestService: DigestService) : UniquenessProviderFactory {
    private var database: CordaPersistence? = null
    private val notaryConfig = JPANotaryConfiguration(maxInputStates = 10)
    private val notaryWorkerName = CordaX500Name.parse("CN=NotaryWorker, O=Corda, L=London, C=GB")

    override fun create(clock: Clock): UniquenessProvider {
        database?.close()
        database = configureDatabase(
                makeTestDataSourceProperties(),
                DatabaseConfig(),
                { null },
                { null },
                NodeSchemaService(extraSchemas = setOf(JPANotarySchemaV1))
        )
        return JPAUniquenessProvider(
                clock,
                database!!,
                notaryConfig,
                notaryWorkerName,
                ::signBatch
        )
    }

    override fun close() {
        database?.close()
    }

    override fun toString(): String = "JPA"

    fun signBatch(it: Iterable<SecureHash>): BatchSignature {
        val root = MerkleTree.getMerkleTree(it.map { it.reHash() }, digestService)

        val signableMetadata = SignatureMetadata(4, Crypto.findSignatureScheme(pubKey).schemeNumberID)
        val signature = keyService.sign(SignableData(root.hash, signableMetadata), pubKey)
        return BatchSignature(signature, root)
    }
}

var ourKeyPair: KeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
val keyService = MockKeyManagementService(makeTestIdentityService(), ourKeyPair)
val pubKey = keyService.freshKey()

fun signSingle(it: SecureHash) = keyService.sign(
        SignableData(
                txId = it,
                signatureMetadata = SignatureMetadata(
                        4,
                        Crypto.findSignatureScheme(pubKey).schemeNumberID
                )
        ), pubKey
)
