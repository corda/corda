package com.r3corda.node.services

import com.r3corda.core.contracts.TimestampCommand
import com.r3corda.core.contracts.TransactionBuilder
import com.r3corda.core.seconds
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.internal.testing.issueState
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.protocols.NotaryError
import com.r3corda.protocols.NotaryException
import com.r3corda.protocols.NotaryProtocol
import org.junit.Before
import org.junit.Test
import java.time.Instant
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
                advertisedServices = *arrayOf(NetworkMapService.Type, SimpleNotaryService.Type)
        )
        clientNode = net.createNode(networkMapAddress = notaryNode.info)
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should sign a unique transaction with a valid timestamp`() {
        val inputState = issueState(clientNode)
        val tx = TransactionBuilder().withItems(inputState)
        tx.setTime(Instant.now(), DUMMY_NOTARY, 30.seconds)
        val wtx = tx.toWireTransaction()

        val protocol = NotaryProtocol.Client(wtx)
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val signature = future.get()
        signature.verifyWithECDSA(wtx.serialized)
    }

    @Test fun `should sign a unique transaction without a timestamp`() {
        val inputState = issueState(clientNode)
        val wtx = TransactionBuilder().withItems(inputState).toWireTransaction()

        val protocol = NotaryProtocol.Client(wtx)
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val signature = future.get()
        signature.verifyWithECDSA(wtx.serialized)
    }

    @Test fun `should report error for transaction with an invalid timestamp`() {
        val inputState = issueState(clientNode)
        val tx = TransactionBuilder().withItems(inputState)
        tx.setTime(Instant.now().plusSeconds(3600), DUMMY_NOTARY, 30.seconds)
        val wtx = tx.toWireTransaction()

        val protocol = NotaryProtocol.Client(wtx)
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val error = (ex.cause as NotaryException).error
        assertTrue(error is NotaryError.TimestampInvalid)
    }

    @Test fun `should report error for transaction with more than one timestamp`() {
        val inputState = issueState(clientNode)
        val tx = TransactionBuilder().withItems(inputState)
        val timestamp = TimestampCommand(Instant.now(), 30.seconds)
        tx.addCommand(timestamp, DUMMY_NOTARY.owningKey)
        tx.addCommand(timestamp, DUMMY_NOTARY.owningKey)
        val wtx = tx.toWireTransaction()

        val protocol = NotaryProtocol.Client(wtx)
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val error = (ex.cause as NotaryException).error
        assertTrue(error is NotaryError.MoreThanOneTimestamp)
    }

    @Test fun `should report conflict for a duplicate transaction`() {
        val inputState = issueState(clientNode)
        val wtx = TransactionBuilder().withItems(inputState).toWireTransaction()

        val firstSpend = NotaryProtocol.Client(wtx)
        val secondSpend = NotaryProtocol.Client(wtx)
        clientNode.smm.add("${NotaryProtocol.TOPIC}.first", firstSpend)
        val future = clientNode.smm.add("${NotaryProtocol.TOPIC}.second", secondSpend)

        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val notaryError = (ex.cause as NotaryException).error as NotaryError.Conflict
        assertEquals(notaryError.tx, wtx)
        notaryError.conflict.verified()
    }
}