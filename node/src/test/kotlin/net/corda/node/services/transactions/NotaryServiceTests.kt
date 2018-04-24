package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.generateSignature
import net.corda.core.messaging.MessageRecipients
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.internal.StartedNode
import net.corda.node.services.messaging.Message
import net.corda.node.services.statemachine.InitialSessionMessage
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.*
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
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notaryServices: ServiceHub
    private lateinit var aliceNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.testing.contracts"))
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        notaryServices = mockNet.defaultNotaryNode.services //TODO get rid of that
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.services.myInfo.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should sign a unique transaction with a valid time-window`() {
        val stx = run {
            val inputState = issueState(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
                    .setTimeWindow(Instant.now(), 30.seconds)
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test
    fun `should sign a unique transaction without a time-window`() {
        val stx = run {
            val inputState = issueState(aliceNode.services, alice)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test
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

    @Test
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

    @Test
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
            assertEquals(consumedStates[firstState.ref]!!.hashOfTransactionId, firstSpendTx.id.sha256())
            assertEquals(consumedStates[secondState.ref]!!.hashOfTransactionId, secondSpendTx.id.sha256())
        }
    }

    @Test
    fun `should reject when notarisation request not signed by the requesting party`() {
        runNotarisationAndInterceptClientPayload { originalPayload ->
            val transaction = originalPayload.signedTransaction
            val randomKeyPair = Crypto.generateKeyPair()
            val bytesToSign = NotarisationRequest(transaction.inputs, transaction.id).serialize().bytes
            val modifiedSignature = NotarisationRequestSignature(randomKeyPair.sign(bytesToSign), aliceNode.services.myInfo.platformVersion)
            originalPayload.copy(requestSignature = modifiedSignature)
        }
    }

    @Test
    fun `should reject when incorrect notarisation request signed - inputs don't match`() {
        runNotarisationAndInterceptClientPayload { originalPayload ->
            val transaction = originalPayload.signedTransaction
            val wrongInputs = listOf(StateRef(SecureHash.randomSHA256(), 0))
            val request = NotarisationRequest(wrongInputs, transaction.id)
            val modifiedSignature = request.generateSignature(aliceNode.services)
            originalPayload.copy(requestSignature = modifiedSignature)
        }
    }

    @Test
    fun `should reject when incorrect notarisation request signed - transaction id doesn't match`() {
        runNotarisationAndInterceptClientPayload { originalPayload ->
            val transaction = originalPayload.signedTransaction
            val wrongTransactionId = SecureHash.randomSHA256()
            val request = NotarisationRequest(transaction.inputs, wrongTransactionId)
            val modifiedSignature = request.generateSignature(aliceNode.services)
            originalPayload.copy(requestSignature = modifiedSignature)
        }
    }

    private fun runNotarisationAndInterceptClientPayload(payloadModifier: (NotarisationPayload) -> NotarisationPayload) {
        aliceNode.setMessagingServiceSpy(object : MessagingServiceSpy(aliceNode.network) {
            override fun send(message: Message, target: MessageRecipients, retryId: Long?, sequenceKey: Any) {
                val messageData = message.data.deserialize<Any>() as? InitialSessionMessage
                val payload = messageData?.firstPayload!!.deserialize()

                if (payload is NotarisationPayload) {
                    val alteredPayload = payloadModifier(payload)
                    val alteredMessageData = messageData.copy(firstPayload = alteredPayload.serialize())
                    val alteredMessage = InMemoryMessage(message.topic, OpaqueBytes(alteredMessageData.serialize().bytes), message.uniqueMessageId)
                    messagingService.send(alteredMessage, target, retryId)

                } else {
                    messagingService.send(message, target, retryId)
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

    private fun issueState(services: ServiceHub, identity: Party): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = services.signInitialTransaction(tx)
        val stx = notaryServices.addSignature(signedByNode, notary.owningKey)
        services.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
