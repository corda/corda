package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.api.StartedNodeServices
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotaryServiceTests {
    private lateinit var mockNet: MockNetwork
    private lateinit var notaryServices: StartedNodeServices
    private lateinit var aliceServices: StartedNodeServices
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(cordappPackages = listOf("net.corda.testing.contracts"))
        aliceServices = mockNet.createNode(MockNodeParameters(legalName = ALICE_NAME)).services
        notaryServices = mockNet.defaultNotaryNode.services //TODO get rid of that
        notary = mockNet.defaultNotaryIdentity
        alice = aliceServices.myInfo.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should sign a unique transaction with a valid time-window`() {
        val stx = run {
            val inputState = issueState(aliceServices, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
                    .setTimeWindow(Instant.now(), 30.seconds)
            aliceServices.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test
    fun `should sign a unique transaction without a time-window`() {
        val stx = run {
            val inputState = issueState(aliceServices, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceServices.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test
    fun `should report error for transaction with an invalid time-window`() {
        val stx = run {
            val inputState = issueState(aliceServices, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
                    .setTimeWindow(Instant.now().plusSeconds(3600), 30.seconds)
            aliceServices.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        assertThat(ex.error).isInstanceOf(NotaryError.TimeWindowInvalid::class.java)
    }

    @Test
    fun `should sign identical transaction multiple times (notarisation is idempotent)`() {
        val stx = run {
            val inputState = issueState(aliceServices, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceServices.signInitialTransaction(tx)
        }

        val firstAttempt = NotaryFlow.Client(stx)
        val secondAttempt = NotaryFlow.Client(stx)
        val f1 = aliceServices.startFlow(firstAttempt)
        val f2 = aliceServices.startFlow(secondAttempt)

        mockNet.runNetwork()

        // Note that the notary will only return identical signatures when using deterministic signature
        // schemes (e.g. EdDSA) and when deterministic metadata is attached (no timestamps or nonces).
        // We only really care that both signatures are over the same transaction and by the same notary.
        val sig1 = f1.getOrThrow().single()
        assertEquals(sig1.by, notary.owningKey)
        assertTrue(sig1.isValid(stx.id))

        val sig2 = f2.getOrThrow().single()
        assertEquals(sig2.by, notary.owningKey)
        assertTrue(sig2.isValid(stx.id))
    }

    @Test
    fun `should report conflict when inputs are reused across transactions`() {
        val inputState = issueState(aliceServices, alice)
        val stx = run {
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceServices.signInitialTransaction(tx)
        }
        val stx2 = run {
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addInputState(issueState(aliceServices, alice))
                    .addCommand(dummyCommand(alice.owningKey))
            aliceServices.signInitialTransaction(tx)
        }

        val firstSpend = NotaryFlow.Client(stx)
        val secondSpend = NotaryFlow.Client(stx2) // Double spend the inputState in a second transaction.
        aliceServices.startFlow(firstSpend)
        val future = aliceServices.startFlow(secondSpend)

        mockNet.runNetwork()

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        val notaryError = ex.error as NotaryError.Conflict
        assertEquals(notaryError.txId, stx2.id)
        notaryError.conflict.verified()
    }

    private fun runNotaryClient(stx: SignedTransaction): CordaFuture<List<TransactionSignature>> {
        val flow = NotaryFlow.Client(stx)
        val future = aliceServices.startFlow(flow)
        mockNet.runNetwork()
        return future
    }

    private fun issueState(services: ServiceHub, identity: Party): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = services.signInitialTransaction(tx)
        val stx = notaryServices.addSignature(signedByNode, notary.owningKey)
        services.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
