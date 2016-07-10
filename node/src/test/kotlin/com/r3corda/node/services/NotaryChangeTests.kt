package com.r3corda.node.services

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.internal.testing.issueMultiPartyState
import com.r3corda.node.internal.testing.issueState
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import org.junit.Before
import org.junit.Test
import protocols.NotaryChangeProtocol
import protocols.NotaryChangeProtocol.Instigator
import protocols.StateReplacementException
import protocols.StateReplacementRefused
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotaryChangeTests {
    lateinit var net: MockNetwork
    lateinit var oldNotaryNode: MockNetwork.MockNode
    lateinit var newNotaryNode: MockNetwork.MockNode
    lateinit var clientNodeA: MockNetwork.MockNode
    lateinit var clientNodeB: MockNetwork.MockNode

    @Before
    fun setup() {
        net = MockNetwork()
        oldNotaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                keyPair = DUMMY_NOTARY_KEY,
                advertisedServices = *arrayOf(NetworkMapService.Type, SimpleNotaryService.Type))
        clientNodeA = net.createNode(networkMapAddress = oldNotaryNode.info)
        clientNodeB = net.createNode(networkMapAddress = oldNotaryNode.info)
        newNotaryNode = net.createNode(networkMapAddress = oldNotaryNode.info, advertisedServices = SimpleNotaryService.Type)

        net.runNetwork() // Clear network map registration messages
    }

    @Test
    fun `should change notary for a state with single participant`() {
        val state = issueState(clientNodeA)
        val newNotary = newNotaryNode.info.identity
        val protocol = Instigator(state, newNotary)
        val future = clientNodeA.smm.add(NotaryChangeProtocol.TOPIC, protocol)

        net.runNetwork()

        val newState = future.get()
        assertEquals(newState.state.notary, newNotary)
    }

    @Test
    fun `should change notary for a state with multiple participants`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB)
        val newNotary = newNotaryNode.info.identity
        val protocol = Instigator(state, newNotary)
        val future = clientNodeA.smm.add(NotaryChangeProtocol.TOPIC, protocol)

        net.runNetwork()

        val newState = future.get()
        assertEquals(newState.state.notary, newNotary)
        val loadedStateA = clientNodeA.services.loadState(newState.ref)
        val loadedStateB = clientNodeB.services.loadState(newState.ref)
        assertEquals(loadedStateA, loadedStateB)
    }

    @Test
    fun `should throw when a participant refuses to change Notary`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB)
        val newEvilNotary = Party("Evil Notary", generateKeyPair().public)
        val protocol = Instigator(state, newEvilNotary)
        val future = clientNodeA.smm.add(NotaryChangeProtocol.TOPIC, protocol)

        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val error = (ex.cause as StateReplacementException).error
        assertTrue(error is StateReplacementRefused)
    }

    // TODO: Add more test cases once we have a general protocol/service exception handling mechanism:
    //       - A participant is offline/can't be found on the network
    //       - The requesting party is not a participant
    //       - The requesting party wants to change additional state fields
    //       - Multiple states in a single "notary change" transaction
    //       - Transaction contains additional states and commands with business logic
}