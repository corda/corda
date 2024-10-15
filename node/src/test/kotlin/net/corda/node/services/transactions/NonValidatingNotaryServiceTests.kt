package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sign
import net.corda.core.flows.NotarisationPayload
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.internal.notary.generateSignature
import net.corda.core.messaging.MessageRecipients
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.messaging.Message
import net.corda.node.services.statemachine.InitialSessionMessage
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InMemoryMessage
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MessagingServiceSpy
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NonValidatingNotaryServiceTests {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notaryNode: TestStartedNode
    private lateinit var aliceNode: TestStartedNode
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME, false))
        )
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        notaryNode = mockNet.defaultNotaryNode
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
	fun `should sign a unique transaction with a valid time-window`() {
        val stx = run {
            val input = issueState(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(input)
                    .addCommand(dummyCommand(alice.owningKey))
                    .setTimeWindow(Instant.now(), 30.seconds)
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test(timeout=300_000)
	fun `should sign a unique transaction without a time-window`() {
        val stx = run {
            val inputStates = issueStates(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputStates[0])
                    .addInputState(inputStates[1])
                    .addCommand(dummyCommand(alice.owningKey))
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test(timeout=300_000)
	fun `should re-sign a transaction with an expired time-window`() {
        val stx = run {
            val inputState = issueState(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
                    .setTimeWindow(Instant.now(), 30.seconds)
            aliceNode.services.signInitialTransaction(tx)
        }

        val sig1 = runNotaryClient(stx).getOrThrow().single()
        assertEquals(sig1.by, notary.owningKey)
        assertTrue(sig1.isValid(stx.id))

        mockNet.nodes.forEach {
            val nodeClock = (it.started!!.services.clock as TestClock)
            nodeClock.advanceBy(Duration.ofDays(1))
        }

        val sig2 = runNotaryClient(stx).getOrThrow().single()
        assertEquals(sig2.by, notary.owningKey)
    }

    @Test(timeout=300_000)
	fun `should report error for transaction with an invalid time-window`() {
        val stx = run {
            val inputState = issueState(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
                    .setTimeWindow(Instant.now().plusSeconds(3600), 30.seconds)
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        assertThat(ex.error).isInstanceOf(NotaryError.TimeWindowInvalid::class.java)
    }

    @Test(timeout=300_000)
	fun `notarise issue tx with time-window`() {
        val stx = run {
            val tx = DummyContract.generateInitial(Random().nextInt(), notary, alice.ref(0))
                        .setTimeWindow(Instant.now(), 30.seconds)
            aliceNode.services.signInitialTransaction(tx)
        }

        val sig = runNotaryClient(stx).getOrThrow().single()
        assertEquals(sig.by, notary.owningKey)
    }

    @Test(timeout=300_000)
	fun `should sign identical transaction multiple times (notarisation is idempotent)`() {
        val stx = run {
            val inputState = issueState(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceNode.services.signInitialTransaction(tx)
        }

        val firstAttempt = NotaryFlow.Client(stx)
        val secondAttempt = NotaryFlow.Client(stx)
        val f1 = aliceNode.services.startFlow(firstAttempt).resultFuture
        val f2 = aliceNode.services.startFlow(secondAttempt).resultFuture

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

    @Test(timeout=300_000)
	fun `should report conflict when inputs are reused across transactions`() {
        val firstState = issueState(aliceNode.services, alice)
        val secondState = issueState(aliceNode.services, alice)

        fun spendState(state: StateAndRef<*>): SignedTransaction {
            val stx = run {
                val tx = TransactionBuilder(notary)
                        .addInputState(state)
                        .addCommand(dummyCommand(alice.owningKey))
                aliceNode.services.signInitialTransaction(tx)
            }
            aliceNode.services.startFlow(NotaryFlow.Client(stx))
            mockNet.runNetwork()
            return stx
        }

        val firstSpendTx = spendState(firstState)
        val secondSpendTx = spendState(secondState)

        val doubleSpendTx = run {
            val tx = TransactionBuilder(notary)
                    .addInputState(issueState(aliceNode.services, alice))
                    .addInputState(firstState)
                    .addInputState(secondState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceNode.services.signInitialTransaction(tx)
        }

        val doubleSpend = NotaryFlow.Client(doubleSpendTx) // Double spend the inputState in a second transaction.
        val future = aliceNode.services.startFlow(doubleSpend)
        mockNet.runNetwork()

        val ex = assertFailsWith(NotaryException::class) { future.resultFuture.getOrThrow() }
        val notaryError = ex.error as NotaryError.Conflict
        assertEquals(notaryError.txId, doubleSpendTx.id)
        with(notaryError) {
            assertEquals(consumedStates.size, 2)
            assertEquals(consumedStates[firstState.ref]!!.hashOfTransactionId, firstSpendTx.id.reHash())
            assertEquals(consumedStates[secondState.ref]!!.hashOfTransactionId, secondSpendTx.id.reHash())
        }
    }

    @Test(timeout=300_000)
	fun `should reject when notarisation request not signed by the requesting party`() {
        runNotarisationAndInterceptClientPayload { originalPayload ->
            val transaction = originalPayload.coreTransaction
            val randomKeyPair = Crypto.generateKeyPair()
            val bytesToSign = NotarisationRequest(transaction.inputs, transaction.id).serialize().bytes
            val modifiedSignature = NotarisationRequestSignature(randomKeyPair.sign(bytesToSign), aliceNode.services.myInfo.platformVersion)
            originalPayload.copy(requestSignature = modifiedSignature)
        }
    }

    @Test(timeout=300_000)
	fun `should reject when incorrect notarisation request signed - inputs don't match`() {
        runNotarisationAndInterceptClientPayload { originalPayload ->
            val transaction = originalPayload.coreTransaction
            val wrongInputs = listOf(StateRef(SecureHash.randomSHA256(), 0))
            val request = NotarisationRequest(wrongInputs, transaction.id)
            val modifiedSignature = request.generateSignature(aliceNode.services)
            originalPayload.copy(requestSignature = modifiedSignature)
        }
    }

    @Test(timeout=300_000)
	fun `should reject when incorrect notarisation request signed - transaction id doesn't match`() {
        runNotarisationAndInterceptClientPayload { originalPayload ->
            val transaction = originalPayload.coreTransaction
            val wrongTransactionId = SecureHash.randomSHA256()
            val request = NotarisationRequest(transaction.inputs, wrongTransactionId)
            val modifiedSignature = request.generateSignature(aliceNode.services)
            originalPayload.copy(requestSignature = modifiedSignature)
        }
    }

    @Test(timeout=300_000)
	fun `should reject a transaction with too many inputs`() {
        NotaryServiceTests.notariseWithTooManyInputs(aliceNode, alice, notary, mockNet)
    }

    private fun runNotarisationAndInterceptClientPayload(payloadModifier: (NotarisationPayload) -> NotarisationPayload) {
        aliceNode.setMessagingServiceSpy(object : MessagingServiceSpy() {
            override fun send(message: Message, target: MessageRecipients, sequenceKey: Any) {
                val messageData = message.data.deserialize<Any>() as? InitialSessionMessage
                val payload = messageData?.firstPayload!!.deserialize()

                if (payload is NotarisationPayload) {
                    val alteredPayload = payloadModifier(payload)
                    val alteredMessageData = messageData.copy(firstPayload = alteredPayload.serialize())
                    val alteredMessage = InMemoryMessage(message.topic, OpaqueBytes(alteredMessageData.serialize().bytes), message.uniqueMessageId)
                    messagingService.send(alteredMessage, target)
                } else {
                    messagingService.send(message, target)
                }
            }
        })

        val stx = run {
            val inputState = issueState(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        assertThat(ex.error).isInstanceOf(NotaryError.RequestSignatureInvalid::class.java)
    }

    private fun runNotaryClient(stx: SignedTransaction): CordaFuture<List<TransactionSignature>> {
        val flow = NotaryFlow.Client(stx)
        val future = aliceNode.services.startFlow(flow).resultFuture
        mockNet.runNetwork()
        return future
    }

    private fun issueState(serviceHub: ServiceHub, identity: Party): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = serviceHub.signInitialTransaction(tx)
        val stx = notaryNode.services.addSignature(signedByNode, notary.owningKey)
        serviceHub.recordTransactions(stx)
        return StateAndRef(stx.coreTransaction.outputs.first(), StateRef(stx.id, 0))
    }

    private fun issueStates(serviceHub: ServiceHub, identity: Party): List<StateAndRef<*>> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = serviceHub.signInitialTransaction(tx)
        val stx = notaryNode.services.addSignature(signedByNode, notary.owningKey)
        serviceHub.recordTransactions(stx)
        return listOf(StateAndRef(stx.coreTransaction.outputs[0], StateRef(stx.id, 0)),
                StateAndRef(stx.coreTransaction.outputs[1], StateRef(stx.id, 1)))
    }
}
