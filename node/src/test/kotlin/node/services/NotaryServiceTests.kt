package node.services

import core.contracts.TransactionBuilder
import core.seconds
import core.testing.DUMMY_NOTARY
import core.testing.DUMMY_NOTARY_KEY
import node.internal.testing.MockNetwork
import node.testutils.issueState
import org.junit.Before
import org.junit.Test
import protocols.NotaryError
import protocols.NotaryException
import protocols.NotaryProtocol
import java.time.Instant
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotaryServiceTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode

    @Before
    fun setup() {
        // TODO: Move into MockNetwork
        net = MockNetwork()
        notaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        clientNode = net.createPartyNode(networkMapAddr = notaryNode.info)
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should sign a unique transaction with a valid timestamp`() {
        val inputState = issueState(clientNode)
        val tx = TransactionBuilder().withItems(inputState)
        tx.setTime(Instant.now(), DUMMY_NOTARY, 30.seconds)
        var wtx = tx.toWireTransaction()

        val protocol = NotaryProtocol(wtx, NotaryProtocol.Companion.tracker())
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val signature = future.get()
        signature.verifyWithECDSA(wtx.serialized)
    }

    @Test fun `should sign a unique transaction without a timestamp`() {
        val inputState = issueState(clientNode)
        val wtx = TransactionBuilder().withItems(inputState).toWireTransaction()

        val protocol = NotaryProtocol(wtx, NotaryProtocol.Companion.tracker())
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val signature = future.get()
        signature.verifyWithECDSA(wtx.serialized)
    }

    @Test fun `should report error for transaction with an invalid timestamp`() {
        val inputState = issueState(clientNode)
        val tx = TransactionBuilder().withItems(inputState)
        tx.setTime(Instant.now().plusSeconds(3600), DUMMY_NOTARY, 30.seconds)
        var wtx = tx.toWireTransaction()

        val protocol = NotaryProtocol(wtx, NotaryProtocol.Companion.tracker())
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val error = (ex.cause as NotaryException).error
        assertTrue(error is NotaryError.TimestampInvalid)
    }

    @Test fun `should report conflict for a duplicate transaction`() {
        val inputState = issueState(clientNode)
        val wtx = TransactionBuilder().withItems(inputState).toWireTransaction()

        val firstSpend = NotaryProtocol(wtx)
        val secondSpend = NotaryProtocol(wtx)
        clientNode.smm.add("${NotaryProtocol.TOPIC}.first", firstSpend)
        val future = clientNode.smm.add("${NotaryProtocol.TOPIC}.second", secondSpend)
        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val notaryError = (ex.cause as NotaryException).error as NotaryError.Conflict
        assertEquals(notaryError.tx, wtx)
        notaryError.conflict.verified()
    }
}