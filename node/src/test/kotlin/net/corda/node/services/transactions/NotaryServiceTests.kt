package net.corda.node.services.transactions

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.testing.contracts.DummyContract
import net.corda.core.crypto.DigitalSignature
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.services.network.NetworkMapService
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotaryServiceTests {
    lateinit var mockNet: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode

    @Before
    fun setup() {
        mockNet = MockNetwork()
        notaryNode = mockNet.createNode(
                legalName = DUMMY_NOTARY.name,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type)))
        clientNode = mockNet.createNode(networkMapAddress = notaryNode.network.myAddress)
        mockNet.runNetwork() // Clear network map registration messages
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should sign a unique transaction with a valid time-window`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.setTimeWindow(Instant.now(), 30.seconds)
            clientNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test
    fun `should sign a unique transaction without a time-window`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            clientNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)
        val signatures = future.getOrThrow()
        signatures.forEach { it.verify(stx.id) }
    }

    @Test
    fun `should report error for transaction with an invalid time-window`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.setTimeWindow(Instant.now().plusSeconds(3600), 30.seconds)
            clientNode.services.signInitialTransaction(tx)
        }

        val future = runNotaryClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        assertThat(ex.error).isInstanceOf(NotaryError.TimeWindowInvalid::class.java)
    }

    @Test
    fun `should sign identical transaction multiple times (signing is idempotent)`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            clientNode.services.signInitialTransaction(tx)
        }

        val firstAttempt = NotaryFlow.Client(stx)
        val secondAttempt = NotaryFlow.Client(stx)
        val f1 = clientNode.services.startFlow(firstAttempt)
        val f2 = clientNode.services.startFlow(secondAttempt)

        mockNet.runNetwork()

        assertEquals(f1.resultFuture.getOrThrow(), f2.resultFuture.getOrThrow())
    }

    @Test
    fun `should report conflict when inputs are reused across transactions`() {
        val inputState = issueState(clientNode)
        val stx = run {
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            clientNode.services.signInitialTransaction(tx)
        }
        val stx2 = run {
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.addInputState(issueState(clientNode))
            clientNode.services.signInitialTransaction(tx)
        }

        val firstSpend = NotaryFlow.Client(stx)
        val secondSpend = NotaryFlow.Client(stx2) // Double spend the inputState in a second transaction.
        clientNode.services.startFlow(firstSpend)
        val future = clientNode.services.startFlow(secondSpend)

        mockNet.runNetwork()

        val ex = assertFailsWith(NotaryException::class) { future.resultFuture.getOrThrow() }
        val notaryError = ex.error as NotaryError.Conflict
        assertEquals(notaryError.txId, stx2.id)
        notaryError.conflict.verified()
    }

    private fun runNotaryClient(stx: SignedTransaction): ListenableFuture<List<DigitalSignature.WithKey>> {
        val flow = NotaryFlow.Client(stx)
        val future = clientNode.services.startFlow(flow).resultFuture
        mockNet.runNetwork()
        return future
    }

    fun issueState(node: AbstractNode): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notaryNode.info.notaryIdentity, node.info.legalIdentity.ref(0))
        val signedByNode = node.services.signInitialTransaction(tx)
        val stx = notaryNode.services.addSignature(signedByNode, notaryNode.services.notaryIdentityKey)
        node.services.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
