package com.r3corda.node.services

import com.r3corda.core.contracts.TransactionBuilder
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.internal.testing.issueInvalidState
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.ValidatingNotaryService
import com.r3corda.protocols.NotaryError
import com.r3corda.protocols.NotaryException
import com.r3corda.protocols.NotaryProtocol
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidatingNotaryServiceTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode

    @Before fun setup() {
        net = MockNetwork()
        notaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                keyPair = DUMMY_NOTARY_KEY,
                advertisedServices = *arrayOf(NetworkMapService.Type, ValidatingNotaryService.Type)
        )
        clientNode = net.createNode(networkMapAddress = notaryNode.info)
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should report error for invalid transaction dependency`() {
        val inputState = issueInvalidState(clientNode)
        val wtx = TransactionBuilder().withItems(inputState).toWireTransaction()

        val protocol = NotaryProtocol.Client(wtx)
        val future = clientNode.smm.add(NotaryProtocol.TOPIC, protocol)
        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val notaryError = (ex.cause as NotaryException).error
        assertTrue(notaryError is NotaryError.TransactionInvalid, "Received wrong Notary error")
    }
}