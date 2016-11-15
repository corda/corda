package net.corda.node.services

import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.generateKeyPair
import net.corda.core.node.services.ServiceInfo
import net.corda.core.seconds
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.node.internal.AbstractNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.protocols.NotaryChangeProtocol.Instigator
import net.corda.protocols.StateReplacementException
import net.corda.protocols.StateReplacementRefused
import net.corda.testing.node.MockNetwork
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
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(SimpleNotaryService.type)))
        clientNodeA = net.createNode(networkMapAddress = oldNotaryNode.info.address)
        clientNodeB = net.createNode(networkMapAddress = oldNotaryNode.info.address)
        newNotaryNode = net.createNode(networkMapAddress = oldNotaryNode.info.address, advertisedServices = ServiceInfo(SimpleNotaryService.type))

        net.runNetwork() // Clear network map registration messages
    }

    @Test
    fun `should change notary for a state with single participant`() {
        val state = issueState(clientNodeA, oldNotaryNode)
        val newNotary = newNotaryNode.info.notaryIdentity
        val protocol = Instigator(state, newNotary)
        val future = clientNodeA.services.startProtocol(protocol)

        net.runNetwork()

        val newState = future.resultFuture.get()
        assertEquals(newState.state.notary, newNotary)
    }

    @Test
    fun `should change notary for a state with multiple participants`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB, oldNotaryNode)
        val newNotary = newNotaryNode.info.notaryIdentity
        val protocol = Instigator(state, newNotary)
        val future = clientNodeA.services.startProtocol(protocol)

        net.runNetwork()

        val newState = future.resultFuture.get()
        assertEquals(newState.state.notary, newNotary)
        val loadedStateA = clientNodeA.services.loadState(newState.ref)
        val loadedStateB = clientNodeB.services.loadState(newState.ref)
        assertEquals(loadedStateA, loadedStateB)
    }

    @Test
    fun `should throw when a participant refuses to change Notary`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB, oldNotaryNode)
        val newEvilNotary = Party("Evil Notary", generateKeyPair().public)
        val protocol = Instigator(state, newEvilNotary)
        val future = clientNodeA.services.startProtocol(protocol)

        net.runNetwork()

        val ex = assertFailsWith(ExecutionException::class) { future.resultFuture.get() }
        val error = (ex.cause as StateReplacementException).error
        assertTrue(error is StateReplacementRefused)
    }

    // TODO: Add more test cases once we have a general protocol/service exception handling mechanism:
    //       - A participant is offline/can't be found on the network
    //       - The requesting party is not a participant
    //       - The requesting party wants to change additional state fields
    //       - Multiple states in a single "notary change" transaction
    //       - Transaction contains additional states and commands with business logic
    //       - The transaction type is not a notary change transaction at all.
}

fun issueState(node: AbstractNode, notaryNode: AbstractNode): StateAndRef<*> {
    val tx = DummyContract.generateInitial(node.info.legalIdentity.ref(0), Random().nextInt(), notaryNode.info.notaryIdentity)
    val nodeKey = node.services.legalIdentityKey
    tx.signWith(nodeKey)
    val notaryKeyPair = notaryNode.services.notaryIdentityKey
    tx.signWith(notaryKeyPair)
    val stx = tx.toSignedTransaction()
    node.services.recordTransactions(listOf(stx))
    return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
}

fun issueMultiPartyState(nodeA: AbstractNode, nodeB: AbstractNode, notaryNode: AbstractNode): StateAndRef<DummyContract.MultiOwnerState> {
    val state = TransactionState(DummyContract.MultiOwnerState(0,
            listOf(nodeA.info.legalIdentity.owningKey, nodeB.info.legalIdentity.owningKey)), notaryNode.info.notaryIdentity)
    val tx = TransactionType.NotaryChange.Builder(notaryNode.info.notaryIdentity).withItems(state)
    val nodeAKey = nodeA.services.legalIdentityKey
    val nodeBKey = nodeB.services.legalIdentityKey
    tx.signWith(nodeAKey)
    tx.signWith(nodeBKey)
    val notaryKeyPair = notaryNode.services.notaryIdentityKey
    tx.signWith(notaryKeyPair)
    val stx = tx.toSignedTransaction()
    nodeA.services.recordTransactions(listOf(stx))
    nodeB.services.recordTransactions(listOf(stx))
    val stateAndRef = StateAndRef(state, StateRef(stx.id, 0))
    return stateAndRef
}

fun issueInvalidState(node: AbstractNode, notary: Party): StateAndRef<*> {
    val tx = DummyContract.generateInitial(node.info.legalIdentity.ref(0), Random().nextInt(), notary)
    tx.setTime(Instant.now(), 30.seconds)
    val nodeKey = node.services.legalIdentityKey
    tx.signWith(nodeKey)
    val stx = tx.toSignedTransaction(false)
    node.services.recordTransactions(listOf(stx))
    return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
}
