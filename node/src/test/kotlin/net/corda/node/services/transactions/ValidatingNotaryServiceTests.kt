/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.flows.*
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
import net.corda.node.internal.StartedNode
import net.corda.node.services.issueInvalidState
import net.corda.node.services.messaging.Message
import net.corda.node.services.statemachine.InitialSessionMessage
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidatingNotaryServiceTests {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notaryNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var aliceNode: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.testing.contracts"))
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        notaryNode = mockNet.defaultNotaryNode
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should report error for invalid transaction dependency`() {
        val stx = run {
            val inputState = issueInvalidState(aliceNode.services, alice, notary)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        val notaryError = ex.error as NotaryError.TransactionInvalid
        assertThat(notaryError.cause).isInstanceOf(SignedTransaction.SignaturesMissingException::class.java)
    }

    @Test
    fun `should report error for missing signatures`() {
        val expectedMissingKey = generateKeyPair().public
        val stx = run {
            val inputState = issueState(aliceNode.services, alice)

            val command = Command(DummyContract.Commands.Move(), expectedMissingKey)
            val tx = TransactionBuilder(notary).withItems(inputState, command)
            aliceNode.services.signInitialTransaction(tx)
        }

        // Expecting SignaturesMissingException instead of NotaryException, since the exception should originate from
        // the client flow.
        val ex = assertFailsWith<SignedTransaction.SignaturesMissingException> {
            val future = runNotaryClient(stx)
            future.getOrThrow()
        }
        val missingKeys = ex.missing
        assertEquals(setOf(expectedMissingKey), missingKeys)
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

    @Test
    fun `should reject a transaction with too many inputs`() {
        NotaryServiceTests.notariseWithTooManyInputs(aliceNode, alice, notary, mockNet)
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

    private fun issueState(serviceHub: ServiceHub, identity: Party): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = serviceHub.signInitialTransaction(tx)
        val stx = notaryNode.services.addSignature(signedByNode, notary.owningKey)
        serviceHub.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
