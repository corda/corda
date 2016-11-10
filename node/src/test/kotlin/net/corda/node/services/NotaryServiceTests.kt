package net.corda.node.services

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.node.services.ServiceInfo
import net.corda.core.crypto.DigitalSignature
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.node.internal.AbstractNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.protocols.NotaryError
import net.corda.protocols.NotaryException
import net.corda.protocols.NotaryProtocol
import net.corda.testing.MINI_CORP_KEY
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotaryServiceTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode

    @Before fun setup() {
        net = MockNetwork()
        notaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                keyPair = DUMMY_NOTARY_KEY,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type)))
        clientNode = net.createNode(networkMapAddress = notaryNode.info.address, keyPair = MINI_CORP_KEY)
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should sign a unique transaction with a valid timestamp`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.setTime(Instant.now(), 30.seconds)
            tx.signWith(clientNode.keyPair!!)
            tx.toSignedTransaction(false)
        }

        val future = runNotaryClient(stx)
        val signature = future.get()
        signature.verifyWithECDSA(stx.id)
    }

    @Test fun `should sign a unique transaction without a timestamp`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.signWith(clientNode.keyPair!!)
            tx.toSignedTransaction(false)
        }

        val future = runNotaryClient(stx)
        val signature = future.get()
        signature.verifyWithECDSA(stx.id)
    }

    @Test fun `should report error for transaction with an invalid timestamp`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.setTime(Instant.now().plusSeconds(3600), 30.seconds)
            tx.signWith(clientNode.keyPair!!)
            tx.toSignedTransaction(false)
        }

        val future = runNotaryClient(stx)

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val error = (ex.cause as NotaryException).error
        assertTrue(error is NotaryError.TimestampInvalid)
    }

    @Test fun `should report conflict for a duplicate transaction`() {
        val stx = run {
            val inputState = issueState(clientNode)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            tx.signWith(clientNode.keyPair!!)
            tx.toSignedTransaction(false)
        }

        val firstSpend = NotaryProtocol.Client(stx)
        val secondSpend = NotaryProtocol.Client(stx)
        clientNode.services.startProtocol(firstSpend)
        val future = clientNode.services.startProtocol(secondSpend)

        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val notaryError = (ex.cause as NotaryException).error as NotaryError.Conflict
        assertEquals(notaryError.tx, stx.tx)
        notaryError.conflict.verified()
    }


    private fun runNotaryClient(stx: SignedTransaction): ListenableFuture<DigitalSignature.LegallyIdentifiable> {
        val protocol = NotaryProtocol.Client(stx)
        val future = clientNode.services.startProtocol(protocol)
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
