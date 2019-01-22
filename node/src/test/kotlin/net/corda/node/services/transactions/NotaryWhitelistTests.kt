package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.*
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.NotaryChangeTransactionBuilder
import net.corda.core.node.NetworkParameters
import net.corda.core.transactions.NotaryChangeLedgerTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.internal.*
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.KeyPair
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class NotaryWhitelistTests(
        /** Specified whether validating notaries should be used. */
        private val isValidating: Boolean
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Validating = {0}")
        fun data(): Collection<Boolean> = listOf(true, false)
    }

    private val oldNotaryName = CordaX500Name("Old Notary", "Zurich", "CH")
    private val newNotaryName = CordaX500Name("New Notary", "Zurich", "CH")

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var oldNotary: Party
    private lateinit var newNotary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                notarySpecs = listOf(MockNetworkNotarySpec(oldNotaryName, validating = isValidating), MockNetworkNotarySpec(newNotaryName, validating = isValidating)),
                initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        )
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        oldNotary = mockNet.notaryNodes[0].info.legalIdentities.last()
        newNotary = mockNet.notaryNodes[1].info.legalIdentities.last()

        alice = aliceNode.services.myInfo.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    /**
     * This test verifies network merging support: when a sub-zone merges into another zone, and _its states are deemed to be low-risk_ by
     * the network operator, the old notary service can temporarily operate on the new zone to facilitate notary change requests (even though
     * it's not whitelisted for regular use).
     */
    @Test
    fun `can perform notary change on a de-listed notary`() {
        // Issue a state using the old notary. It is currently whitelisted.
        val stateFakeNotary = issueStateOnOldNotary(oldNotary)

        // Remove old notary from the whitelist
        val parameters = aliceNode.services.networkParameters
        val newParameters = removeOldNotary(parameters)
        mockNet.nodes.forEach {
            (it.networkParametersStorage as MockNetworkParametersStorage).setCurrentParametersUnverified(newParameters)
        }

        // Re-point the state to the remaining whitelisted notary. The transaction itself should be considered valid, even though the old notary is not whitelisted.
        val futureChange = aliceNode.services.startFlow(NotaryChangeFlow(stateFakeNotary, newNotary)).resultFuture
        mockNet.runNetwork()
        val newSTate = futureChange.getOrThrow()

        // Create a valid transaction consuming the re-pointed state.
        val validTxBuilder = TransactionBuilder(newNotary)
                .addInputState(newSTate)
                .addCommand(dummyCommand(alice.owningKey))
        val validStx = aliceNode.services.signInitialTransaction(validTxBuilder)

        // The transaction verifies.
        validStx.verify(aliceNode.services, false)

        // Notarisation should succeed.
        val future = runNotaryClient(validStx)
        future.getOrThrow()
    }

    /**
     * Following on from the previous one, this test verifies that a non-whitelisted notary cannot be used for regular transactions.
     */
    @Test
    fun `can't perform a regular transaction on a de-listed notary`() {
        // Issue a state using the old notary. It is currently whitelisted.
        val state = issueStateOnOldNotary(oldNotary)

        // Remove old notary from the whitelist
        val parameters = aliceNode.services.networkParameters
        val newParameters = removeOldNotary(parameters)
        mockNet.nodes.forEach {
            (it.networkParametersStorage as MockNetworkParametersStorage).setCurrentParametersUnverified(newParameters)
        }

        // Create a valid transaction consuming the state.
        val validTxBuilder = TransactionBuilder(oldNotary)
                .addInputState(state)
                .addOutputState(state.state.copy())
                .addCommand(dummyCommand(alice.owningKey))
        val validStx = aliceNode.services.signInitialTransaction(validTxBuilder)

        // The transaction does not verify as the notary is no longer whitelisted.
        assertFailsWith<IllegalStateException> {
            validStx.verify(aliceNode.services, false)
        }

        // Notarisation should fail (assuming the unlisted notary is not malicious).
        val future = runNotaryClient(validStx)
        val ex = assertFailsWith(NotaryException::class) {
            future.getOrThrow()
        }
        assert(ex.error is NotaryError.TransactionInvalid)
        assertEquals(validStx.id, ex.txId)
    }

    private fun removeOldNotary(parameters: NetworkParameters): NetworkParameters {
        val newParameters = parameters.copy(notaries = parameters.notaries.drop(1))
        assert(newParameters.notaries.none { it.identity == oldNotary })
        assert(newParameters.notaries.any { it.identity == newNotary })
        return newParameters
    }

    private fun issueStateOnOldNotary(oldNotaryParty: Party): StateAndRef<DummyContract.State> {
        val fakeTxBuilder = DummyContract
                .generateInitial(Random().nextInt(), oldNotaryParty, alice.ref(0))
                .setTimeWindow(Instant.now(), 30.seconds)
        val fakeStx = aliceNode.services.signInitialTransaction(fakeTxBuilder)

        val sigs = runNotaryClient(fakeStx).getOrThrow()
        aliceNode.services.validatedTransactions.addTransaction(fakeStx + sigs)
        return fakeStx.tx.outRef(0)
    }

    private fun runNotaryClient(stx: SignedTransaction): CordaFuture<List<TransactionSignature>> {
        val flow = NotaryFlow.Client(stx)
        val future = aliceNode.services.startFlow(flow).resultFuture
        mockNet.runNetwork()
        return future
    }

    @Test
    fun `should reject transaction when a dependency does not contain notary in whitelist`() {
        Assume.assumeTrue(isValidating) // Skip the test for non-validating notaries

        val fakeNotaryKeyPair = generateKeyPair()
        val fakeNotaryParty = Party(DUMMY_NOTARY_NAME.copy(organisation = "Fake notary"), fakeNotaryKeyPair.public)

        // Issue a state using an unlisted notary. This transaction should not verify when checked by counterparties.
        val stateFakeNotary = issueStateWithFakeNotary(fakeNotaryParty, fakeNotaryKeyPair)

        // Re-point the state to the whitelisted notary. The transaction itself should be considered valid, even though the old notary is not whitelisted.
        val notaryChangeLtx = changeNotary(stateFakeNotary, fakeNotaryParty, fakeNotaryKeyPair)

        // Create a valid transaction consuming the re-pointed state.
        val inputStateValidNotary = notaryChangeLtx.outRef<DummyContract.State>(0)
        val validTxBuilder = TransactionBuilder(oldNotary)
                .addInputState(inputStateValidNotary)
                .addCommand(dummyCommand(alice.owningKey))
        val validStx = aliceNode.services.signInitialTransaction(validTxBuilder)

        // The transaction itself verifies, as no resolution is done here.
        validStx.verify(aliceNode.services, false)

        val future = runNotaryClient(validStx)

        // The notary should reject this transaction – the issue transaction in the dependencies should not verify.
        val ex = assertFailsWith(NotaryException::class) {
            future.getOrThrow()
        }
        assert(ex.error is NotaryError.TransactionInvalid)
        assertEquals(validStx.id, ex.txId)
    }

    private fun issueStateWithFakeNotary(fakeNotaryParty: Party, fakeNotaryKeyPair: KeyPair): StateAndRef<DummyContract.State> {
        val fakeTxBuilder = DummyContract
                .generateInitial(Random().nextInt(), fakeNotaryParty, alice.ref(0))
                .setTimeWindow(Instant.now(), 30.seconds)
        val fakeStx = aliceNode.services.signInitialTransaction(fakeTxBuilder)
        val notarySig = getNotarySig(fakeStx, fakeNotaryKeyPair)
        aliceNode.services.validatedTransactions.addTransaction(fakeStx + notarySig)
        return fakeStx.tx.outRef(0)
    }

    /** Changes the notary service to [notary]. Does not actually communicate with a notary. */
    private fun changeNotary(inputState: StateAndRef<DummyContract.State>, fakeNotaryParty: Party, fakeNotaryKeyPair: KeyPair): NotaryChangeLedgerTransaction {
        val notaryChangeTx = NotaryChangeTransactionBuilder(
                listOf(inputState.ref),
                fakeNotaryParty,
                oldNotary,
                aliceNode.services.networkParametersService.currentHash
        ).build()

        val notaryChangeAliceSig = getAliceSig(notaryChangeTx)

        val notaryChangeNotarySig = run {
            val metadata = SignatureMetadata(4, Crypto.findSignatureScheme(fakeNotaryParty.owningKey).schemeNumberID)
            val data = SignableData(notaryChangeTx.id, metadata)
            fakeNotaryKeyPair.sign(data)
        }
        val notaryChangeStx = SignedTransaction(notaryChangeTx, listOf(notaryChangeAliceSig, notaryChangeNotarySig))

        aliceNode.services.validatedTransactions.addTransaction(notaryChangeStx)

        // Resolving the ledger transaction verifies the whitelist checking logic – for notary change transactions the old notary
        // does not need to be whitelisted.
        val notaryChangeLtx = notaryChangeStx.resolveNotaryChangeTransaction(aliceNode.services)
        notaryChangeLtx.verifyRequiredSignatures()
        return notaryChangeLtx
    }

    private fun getAliceSig(notaryChangeTx: NotaryChangeWireTransaction): TransactionSignature {
        val metadata = SignatureMetadata(4, Crypto.findSignatureScheme(alice.owningKey).schemeNumberID)
        val data = SignableData(notaryChangeTx.id, metadata)
        return aliceNode.services.keyManagementService.sign(data, alice.owningKey)
    }

    private fun getNotarySig(fakeStx: SignedTransaction, fakeNotaryKeyPair: KeyPair): TransactionSignature {
        val metadata = SignatureMetadata(4, Crypto.findSignatureScheme(fakeNotaryKeyPair.public).schemeNumberID)
        val data = SignableData(fakeStx.id, metadata)
        return fakeNotaryKeyPair.sign(data)
    }
}