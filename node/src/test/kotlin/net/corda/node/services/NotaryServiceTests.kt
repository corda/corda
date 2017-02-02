package net.corda.node.services

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.DigitalSignature
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.MINI_CORP_KEY
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotaryServiceTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode
    lateinit var clientKeyPair: KeyPair

    @Before fun setup() {
        net = MockNetwork()
        notaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type)))
        clientNode = net.createNode(networkMapAddress = notaryNode.info.address)
        clientKeyPair = clientNode.keyManagement.toKeyPair(clientNode.info.legalIdentity.owningKey.keys.single())
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should sign a unique transaction with a valid timestamp`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.setTime(Instant.now(), 30.seconds)
            tx.signWith(clientKeyPair)
            tx.toSignedTransaction(false)
        }

        val future = runNotaryClient(stx)
        val signature = future.getOrThrow()
        signature.verifyWithECDSA(stx.id)
    }

    @Test fun `should sign a unique transaction without a timestamp`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.signWith(clientKeyPair)
            tx.toSignedTransaction(false)
        }

        val future = runNotaryClient(stx)
        val signature = future.getOrThrow()
        signature.verifyWithECDSA(stx.id)
    }

    @Test fun `should report error for transaction with an invalid timestamp`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.setTime(Instant.now().plusSeconds(3600), 30.seconds)
            tx.signWith(clientKeyPair)
            tx.toSignedTransaction(false)
        }

        val future = runNotaryClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        assertThat(ex.error).isInstanceOf(NotaryError.TimestampInvalid::class.java)
    }

    @Test fun `should sign identical transaction multiple times (signing is idempotent)`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.signWith(clientKeyPair)
            tx.toSignedTransaction(false)
        }

        val firstAttempt = NotaryFlow.Client(stx)
        val secondAttempt = NotaryFlow.Client(stx)
        val f1 = clientNode.services.startFlow(firstAttempt)
        val f2 = clientNode.services.startFlow(secondAttempt)

        net.runNetwork()

        assertEquals(f1.resultFuture.getOrThrow(), f2.resultFuture.getOrThrow())
    }

    @Test fun `should report conflict when inputs are reused across transactions`() {
        val inputState = issueState(clientNode)
        val stx = run {
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.signWith(clientKeyPair)
            tx.toSignedTransaction(false)
        }
        val stx2 = run {
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.addInputState(issueState(clientNode))
            tx.signWith(clientKeyPair)
            tx.toSignedTransaction(false)
        }

        val firstSpend = NotaryFlow.Client(stx)
        val secondSpend = NotaryFlow.Client(stx2) // Double spend the inputState in a second transaction.
        clientNode.services.startFlow(firstSpend)
        val future = clientNode.services.startFlow(secondSpend)

        net.runNetwork()

        val ex = assertFailsWith(NotaryException::class) { future.resultFuture.getOrThrow() }
        val notaryError = ex.error as NotaryError.Conflict
        assertEquals(notaryError.tx, stx2.tx)
        notaryError.conflict.verified()
    }

    private fun runNotaryClient(stx: SignedTransaction): ListenableFuture<DigitalSignature.WithKey> {
        val flow = NotaryFlow.Client(stx)
        val future = clientNode.services.startFlow(flow).resultFuture
        net.runNetwork()
        return future
    }

    fun issueState(node: AbstractNode): StateAndRef<*> {
        val tx = DummyContract.generateInitial(node.info.legalIdentity.ref(0), Random().nextInt(), notaryNode.info.notaryIdentity)
        val nodeKey = node.services.legalIdentityKey
        tx.signWith(nodeKey)
        val notaryKeyPair = notaryNode.services.notaryIdentityKey
        tx.signWith(notaryKeyPair)
        val stx = tx.toSignedTransaction()
        node.services.recordTransactions(listOf(stx))
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
