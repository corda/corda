package com.r3corda.node.services

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.seconds
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.AbstractNode
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.protocols.NotaryChangeProtocol
import com.r3corda.protocols.NotaryChangeProtocol.Instigator
import com.r3corda.protocols.StateReplacementException
import com.r3corda.protocols.StateReplacementRefused
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.*
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

fun issueState(node: AbstractNode): StateAndRef<*> {
    val tx = DummyContract().generateInitial(node.info.identity.ref(0), Random().nextInt(), DUMMY_NOTARY)
    tx.signWith(node.storage.myLegalIdentityKey)
    tx.signWith(DUMMY_NOTARY_KEY)
    val stx = tx.toSignedTransaction()
    node.services.recordTransactions(listOf(stx))
    return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
}

fun issueMultiPartyState(nodeA: AbstractNode, nodeB: AbstractNode): StateAndRef<DummyContract.MultiOwnerState> {
    val state = TransactionState(DummyContract.MultiOwnerState(0,
            listOf(nodeA.info.identity.owningKey, nodeB.info.identity.owningKey)), DUMMY_NOTARY)
    val tx = TransactionType.NotaryChange.Builder(DUMMY_NOTARY).withItems(state)
    tx.signWith(nodeA.storage.myLegalIdentityKey)
    tx.signWith(nodeB.storage.myLegalIdentityKey)
    tx.signWith(DUMMY_NOTARY_KEY)
    val stx = tx.toSignedTransaction()
    nodeA.services.recordTransactions(listOf(stx))
    nodeB.services.recordTransactions(listOf(stx))
    val stateAndRef = StateAndRef(state, StateRef(stx.id, 0))
    return stateAndRef
}

fun issueInvalidState(node: AbstractNode, notary: Party = DUMMY_NOTARY): StateAndRef<*> {
    val tx = DummyContract().generateInitial(node.info.identity.ref(0), Random().nextInt(), notary)
    tx.setTime(Instant.now(), 30.seconds)
    tx.signWith(node.storage.myLegalIdentityKey)
    val stx = tx.toSignedTransaction(false)
    node.services.recordTransactions(listOf(stx))
    return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
}
