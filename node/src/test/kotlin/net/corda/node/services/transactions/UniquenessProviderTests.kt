package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.PrivacySalt
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
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
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
import net.corda.testing.internal.testTransactionParts
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.MockKeyManagementService
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.KeyPair
import java.time.Clock

@RunWith(Parameterized::class)
class UniquenessProviderTests(
        private val uniquenessProviderFactory: UniquenessProviderFactory,
        private val digestService: DigestService
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
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
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)
    private lateinit var testClock: TestClock
    private lateinit var uniquenessProvider: UniquenessProvider

    @Before
    fun setUp() {
        testClock = TestClock(Clock.systemUTC())
        uniquenessProvider = uniquenessProviderFactory.create(testClock)
        LogHelper.setLevel(uniquenessProvider::class)
        HashAgility.init(txHashAlgoName = digestService.hashAlgorithm)
    }

    @After
    fun tearDown() {
        HashAgility.init()
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
        val tx1Salt = PrivacySalt()
        val timeWindow = TimeWindow.between(
                Clock.systemUTC().instant().plus(30.minutes),
                Clock.systemUTC().instant().plus(60.minutes))
        expectInvalidTimeWindow(tx1Salt, emptyList(), timeWindow)

        // Once time window behaviour has changed, we should add an additional test case here to check
        // that retry within time window still fails. We can't do that now because currently it will
        // succeed and that will result in the past time window case succeeding too.

        // Retry still fails after advancing past time window
        testClock.advanceBy(90.minutes)
        expectInvalidTimeWindow(tx1Salt, emptyList(), timeWindow)
    }

    @Test(timeout=300_000)
    fun `commits transaction within time window`() {
        val tx1Salt = PrivacySalt()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(tx1Salt, emptyList(), timeWindow)

        // Retry is successful whilst still within time window
        testClock.advanceBy(10.minutes)
        expectCommitSuccess(tx1Salt, emptyList(), timeWindow)

        // Retry is successful after time window has expired
        testClock.advanceBy(80.minutes)
        expectCommitSuccess(tx1Salt, emptyList(), timeWindow)
    }

    @Test(timeout=300_000)
    fun `rejects transaction after time window has expired`() {
        val tx1Salt = PrivacySalt()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        expectInvalidTimeWindow(tx1Salt, emptyList(), timeWindow)

        // Retry still fails at a later time
        testClock.advanceBy(10.minutes)
        expectInvalidTimeWindow(tx1Salt, emptyList(), timeWindow)
    }

    @Test(timeout=300_000)
    fun `time window only transactions are processed correctly when duplicate requests occur in succession`() {
        val tx1Salt = PrivacySalt()
        val tx2Salt = PrivacySalt()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        val validFuture1 = commit(tx1Salt, emptyList(), timeWindow).first
        val validFuture2 = commit(tx1Salt, emptyList(), timeWindow).first
        val invalidFuture1 = commit(tx2Salt, emptyList(), invalidTimeWindow).first
        val invalidFuture2 = commit(tx2Salt, emptyList(), invalidTimeWindow).first

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(invalidFuture1.get()).isInstanceOf(Result.Failure::class.java)
        assertThat(invalidFuture2.get()).isInstanceOf(Result.Failure::class.java)
    }

    /* Group B: only reference states */

    @Test(timeout=300_000)
    fun `commits transaction with unused reference states`() {
        val tx1Salt = PrivacySalt()
        val referenceState = generateStateRef()

        expectCommitSuccess(tx1Salt, emptyList(), references = listOf(referenceState))

        // Idempotency: can re-notarise successfully.
        expectCommitSuccess(tx1Salt, emptyList(), references = listOf(referenceState))
    }

    @Test(timeout=300_000)
    fun `rejects transaction with previously used reference states`() {
        val tx1Salt = PrivacySalt()
        val referenceState = generateStateRef()

        val tx1Id = expectCommitSuccess(tx1Salt, listOf(referenceState), references = emptyList())

        // Transaction referencing the spent sate fails.
        val tx2Salt = PrivacySalt()
        val consumedStates = expectConflict(tx2Salt, emptyList(), references = listOf(referenceState))
        assertThat(consumedStates[referenceState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), REFERENCE_INPUT_STATE))
    }

    @Test(timeout=300_000)
    fun `commits retry transaction when reference states were spent since initial transaction`() {
        val tx1Salt = PrivacySalt()
        val referenceState = generateStateRef()

        expectCommitSuccess(tx1Salt, emptyList(), references = listOf(referenceState))

        // Spend reference state
        val tx2Salt = PrivacySalt()
        expectCommitSuccess(tx2Salt, listOf(referenceState), references = emptyList())

        // Retry referencing the now spent state still succeeds
        expectCommitSuccess(tx1Salt, emptyList(), references = listOf(referenceState))
    }

    @Test(timeout=300_000)
    fun `reference state only transactions are processed correctly when duplicate requests occur in succession`() {
        val tx1Salt = PrivacySalt()
        val tx2Salt = PrivacySalt()
        val referenceState = generateStateRef()

        val validFuture3 = commit(tx1Salt, emptyList(), references = listOf(referenceState)).first
        val validFuture4 = commit(tx1Salt, emptyList(), references = listOf(referenceState)).first
        val validFuture1 = commit(tx2Salt, emptyList(), references = listOf(referenceState)).first
        val validFuture2 = commit(tx2Salt, emptyList(), references = listOf(referenceState)).first

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture3.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture4.get()).isInstanceOf(Result.Success::class.java)
    }

    /* Group C: reference states & time window */

    @Test(timeout=300_000)
    fun `commits transaction with unused reference states and valid time window`() {
        val tx1Salt = PrivacySalt()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(tx1Salt, emptyList(), timeWindow, references = listOf(referenceState))

        // The reference state gets consumed.
        expectCommitSuccess(PrivacySalt(), listOf(referenceState), timeWindow)

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        expectCommitSuccess(tx1Salt, emptyList(), timeWindow, references = listOf(referenceState))
    }

    @Test(timeout=300_000)
    fun `rejects transaction with unused reference states and invalid time window`() {
        val tx1Salt = PrivacySalt()
        val referenceState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        expectInvalidTimeWindow(tx1Salt, emptyList(), invalidTimeWindow, references = listOf(referenceState))
    }

    @Test(timeout=300_000)
    fun `rejects transaction with previously used reference states and valid time window`() {
        val tx1Salt = PrivacySalt()
        val referenceState = generateStateRef()

        val tx1Id = expectCommitSuccess(tx1Salt, listOf(referenceState), references = emptyList())

        // Transaction referencing the spent sate fails.
        val tx2Salt = PrivacySalt()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val consumedStates = expectConflict(tx2Salt, emptyList(), timeWindow, references = listOf(referenceState))
        assertThat(consumedStates[referenceState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), REFERENCE_INPUT_STATE))
    }

    @Test(timeout=300_000)
    fun `rejects transaction with previously used reference states and invalid time window`() {
        val tx1Salt = PrivacySalt()
        val referenceState = generateStateRef()

        val tx1Id = expectCommitSuccess(tx1Salt, listOf(referenceState), references = emptyList())

        // Transaction referencing the spent sate fails.
        val tx2Salt = PrivacySalt()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        val consumedStates = expectConflict(tx2Salt, emptyList(), invalidTimeWindow, references = listOf(referenceState))
        assertThat(consumedStates[referenceState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), REFERENCE_INPUT_STATE))
    }

    /* Group D: only input states */

    @Test(timeout=300_000)
    fun `commits transaction with unused inputs`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()

        expectCommitSuccess(tx1Salt, listOf(inputState))

        // Idempotency: can re-notarise successfully.
        expectCommitSuccess(tx1Salt, listOf(inputState))
    }

    @Test(timeout=300_000)
    fun `rejects transaction with previously used inputs`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()

        val inputs = listOf(inputState)
        val tx1Id = expectCommitSuccess(tx1Salt, inputs)

        val tx2Salt = PrivacySalt()

        val consumedStates = expectConflict(tx2Salt, inputs)
        assertThat(consumedStates[inputState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), INPUT_STATE))
    }

    @Test(timeout=300_000)
    fun `input state only transactions are processed correctly when duplicate requests occur in succession`() {
        val tx1Salt = PrivacySalt()
        val tx2Salt = PrivacySalt()
        val inputState = generateStateRef()

        val validFuture1 = commit(tx1Salt, listOf(inputState)).first
        val validFuture2 = commit(tx1Salt, listOf(inputState)).first
        val invalidFuture1 = commit(tx2Salt, listOf(inputState)).first
        val invalidFuture2 = commit(tx2Salt, listOf(inputState)).first

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(invalidFuture1.get()).isInstanceOf(Result.Failure::class.java)
        assertThat(invalidFuture2.get()).isInstanceOf(Result.Failure::class.java)
    }

    /* Group E: input states & time window */

    @Test(timeout=300_000)
    fun `commits transaction with unused inputs and valid time window`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(tx1Salt, listOf(inputState), timeWindow)

        // Idempotency: can re-notarise successfully later.
        testClock.advanceBy(90.minutes)
        expectCommitSuccess(tx1Salt, listOf(inputState), timeWindow)
    }

    @Test(timeout=300_000)
    fun `rejects transaction with unused inputs and invalid time window`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()
        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))

        expectInvalidTimeWindow(tx1Salt, listOf(inputState), invalidTimeWindow)
    }

    @Test(timeout=300_000)
    fun `rejects transaction with previously used inputs and valid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val tx1Salt = PrivacySalt()
        val tx1Id = expectCommitSuccess(tx1Salt, inputs)

        val tx2Salt = PrivacySalt()

        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val consumedStates = expectConflict(tx2Salt, inputs, timeWindow)
        assertThat(consumedStates[inputState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), INPUT_STATE))
    }

    @Test(timeout=300_000)
    fun `rejects transaction with previously used inputs and invalid time window`() {
        val inputState = generateStateRef()
        val inputs = listOf(inputState)
        val tx1Salt = PrivacySalt()
        val tx1Id = expectCommitSuccess(tx1Salt, inputs)

        val tx2Salt = PrivacySalt()

        val invalidTimeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().minus(30.minutes))
        val consumedStates = expectConflict(tx2Salt, inputs, invalidTimeWindow)
        assertThat(consumedStates[inputState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), INPUT_STATE))
    }

    /* Group F: input & reference states */

    @Test(timeout=300_000)
    fun `commits transaction with unused input & reference states`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(tx1Salt, listOf(inputState), timeWindow, references = listOf(referenceState))

        // Idempotency: can re-notarise successfully.
        testClock.advanceBy(90.minutes)
        expectCommitSuccess(tx1Salt, listOf(inputState), timeWindow, references = listOf(referenceState))
    }

    @Test(timeout=300_000)
    fun `re-notarise after reference state is spent`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))

        expectCommitSuccess(tx1Salt, listOf(inputState), timeWindow, references = listOf(referenceState))

        // Spend the reference state.
        expectCommitSuccess(PrivacySalt(), listOf(referenceState), timeWindow)

        // Idempotency: can re-notarise successfully
        testClock.advanceBy(90.minutes)
        val result = commit(tx1Salt, listOf(inputState), timeWindow, references = listOf(referenceState)).first.get()
        // Known failure - this should return success. Will be fixed in a future release.
        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test(timeout=300_000)
    fun `rejects transaction with unused reference states and used input states`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        val tx1Id = expectCommitSuccess(tx1Salt, listOf(inputState), references = emptyList())

        // Transaction referencing the spent sate fails.
        val tx2Salt = PrivacySalt()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val consumedStates = expectConflict(tx2Salt, listOf(inputState), timeWindow, references = listOf(referenceState))
        assertThat(consumedStates[inputState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), INPUT_STATE))
    }

    @Test(timeout=300_000)
    fun `rejects transaction with used reference states and unused input states`() {
        val tx1Salt = PrivacySalt()
        val inputState = generateStateRef()
        val referenceState = generateStateRef()

        val tx1Id = expectCommitSuccess(tx1Salt, listOf(referenceState), references = emptyList())

        // Transaction referencing the spent sate fails.
        val tx2Salt = PrivacySalt()
        val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
        val consumedStates = expectConflict(tx2Salt, listOf(inputState), timeWindow, references = listOf(referenceState))
        assertThat(consumedStates[referenceState]).isEqualTo(StateConsumptionDetails(tx1Id.reHash(), REFERENCE_INPUT_STATE))
    }

    @Test(timeout=300_000)
    fun `input and reference state transactions are processed correctly when duplicate requests occur in succession`() {
        val tx1Salt = PrivacySalt()
        val tx2Salt = PrivacySalt()
        val referenceState = generateStateRef()

        // Ensure batch contains duplicates
        val validFuture1 = commit(tx2Salt, emptyList(), references = listOf(referenceState)).first
        val validFuture2 = commit(tx2Salt, emptyList(), references = listOf(referenceState)).first
        val validFuture3 = commit(tx1Salt, listOf(referenceState)).first

        // Attempt to use the reference state after it has been consumed
        val validFuture4 = commit(PrivacySalt(), emptyList(), references = listOf(referenceState)).first

        // Ensure that transactions are processed correctly and duplicates get the same responses to original
        assertThat(validFuture1.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture2.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture3.get()).isInstanceOf(Result.Success::class.java)
        assertThat(validFuture4.get()).isInstanceOf(Result.Failure::class.java)
    }

    /* Group G: input, reference states and time window – covered by previous tests. */

    /* Transaction signing tests. */
    @Test(timeout=300_000)
    fun `signs transactions correctly`() {
        (1..10).map {
            val inputState1 = generateStateRef()
            val txSalt = PrivacySalt()
            val timeWindow = TimeWindow.untilOnly(Clock.systemUTC().instant().plus(30.minutes))
            commit(txSalt, listOf(inputState1), timeWindow)
        }.forEach { (future, txId) ->
            val result = future.get()
            assertThat(result).isInstanceOf(Result.Success::class.java)
            val signature = (result as Result.Success).signature
            signature.verify(txId)
        }
    }

    private fun commit(
            privacySalt: PrivacySalt,
            inputs: List<StateRef>,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList()
    ): Pair<CordaFuture<Result>, SecureHash> {
        val txParts = testTransactionParts(inputs, timeWindow, references, privacySalt = privacySalt)
        val future = uniquenessProvider.commit(txParts, identity, requestSignature)
        return future to txParts.id
    }

    private fun expectCommitSuccess(
            privacySalt: PrivacySalt,
            inputs: List<StateRef>,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList()
    ): SecureHash {
        val (future, txId) = commit(privacySalt, inputs, timeWindow, references)
        val result = future.get()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        result as Result.Success
        result.signature.verify(txId)
        return txId
    }

    private fun expectCommitFailure(
            privacySalt: PrivacySalt,
            inputs: List<StateRef>,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList()
    ): Pair<NotaryError, SecureHash> {
        val (future, txId) = commit(privacySalt, inputs, timeWindow, references)
        val result = future.get()
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        result as Result.Failure
        return result.error to txId
    }

    private fun expectInvalidTimeWindow(
            privacySalt: PrivacySalt,
            inputs: List<StateRef>,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList()
    ) {
        val notaryError = expectCommitFailure(privacySalt, inputs, timeWindow, references).first
        assertThat(notaryError).isInstanceOf(NotaryError.TimeWindowInvalid::class.java)
        assertThat((notaryError as NotaryError.TimeWindowInvalid).txTimeWindow).isEqualTo(timeWindow)
    }

    private fun expectConflict(
            privacySalt: PrivacySalt,
            inputs: List<StateRef>,
            timeWindow: TimeWindow? = null,
            references: List<StateRef> = emptyList()
    ): Map<StateRef, StateConsumptionDetails> {
        val (notaryError, txId) = expectCommitFailure(privacySalt, inputs, timeWindow, references)
        assertThat(notaryError).isInstanceOf(NotaryError.Conflict::class.java)
        val conflict = notaryError as NotaryError.Conflict
        assertThat(conflict.txId).isEqualTo(txId)
        return conflict.consumedStates
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


class JPAUniquenessProviderFactory(private val digestService: DigestService) : UniquenessProviderFactory {
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
