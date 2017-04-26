package net.corda.node.services

import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.generateKeyPair
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.seconds
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.NotaryChangeFlow.Instigator
import net.corda.flows.StateReplacementException
import net.corda.node.internal.AbstractNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
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
        val flow = Instigator(state, newNotary)
        val future = clientNodeA.services.startFlow(flow)

        net.runNetwork()

        val newState = future.resultFuture.getOrThrow()
        assertEquals(newState.state.notary, newNotary)
    }

    @Test
    fun `should change notary for a state with multiple participants`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB, oldNotaryNode)
        val newNotary = newNotaryNode.info.notaryIdentity
        val flow = Instigator(state, newNotary)
        val future = clientNodeA.services.startFlow(flow)

        net.runNetwork()

        val newState = future.resultFuture.getOrThrow()
        assertEquals(newState.state.notary, newNotary)
        val loadedStateA = clientNodeA.services.loadState(newState.ref)
        val loadedStateB = clientNodeB.services.loadState(newState.ref)
        assertEquals(loadedStateA, loadedStateB)
    }

    @Test
    fun `should throw when a participant refuses to change Notary`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB, oldNotaryNode)
        val newEvilNotary = Party(X500Name("CN=Evil Notary,O=Evil R3,OU=corda,L=London,C=UK"), generateKeyPair().public)
        val flow = Instigator(state, newEvilNotary)
        val future = clientNodeA.services.startFlow(flow)

        net.runNetwork()

        assertThatExceptionOfType(StateReplacementException::class.java).isThrownBy {
            future.resultFuture.getOrThrow()
        }
    }

    @Test
    fun `should not break encumbrance links`() {
        val issueTx = issueEncumberedState(clientNodeA, oldNotaryNode)

        val state = StateAndRef(issueTx.outputs.first(), StateRef(issueTx.id, 0))
        val newNotary = newNotaryNode.info.notaryIdentity
        val flow = Instigator(state, newNotary)
        val future = clientNodeA.services.startFlow(flow)
        net.runNetwork()
        val newState = future.resultFuture.getOrThrow()
        assertEquals(newState.state.notary, newNotary)

        val notaryChangeTx = clientNodeA.services.storageService.validatedTransactions.getTransaction(newState.ref.txhash)!!.tx

        // Check that all encumbrances have been propagated to the outputs
        val originalOutputs = issueTx.outputs.map { it.data }
        val newOutputs = notaryChangeTx.outputs.map { it.data }
        assertTrue(originalOutputs.minus(newOutputs).isEmpty())

        // Check that encumbrance links aren't broken after notary change
        val encumbranceLink = HashMap<ContractState, ContractState?>()
        issueTx.outputs.forEach {
            val currentState = it.data
            val encumbranceState = it.encumbrance?.let { issueTx.outputs[it].data }
            encumbranceLink[currentState] = encumbranceState
        }
        notaryChangeTx.outputs.forEach {
            val currentState = it.data
            val encumbranceState = it.encumbrance?.let { notaryChangeTx.outputs[it].data }
            assertEquals(encumbranceLink[currentState], encumbranceState)
        }
    }

    private fun issueEncumberedState(node: AbstractNode, notaryNode: AbstractNode): WireTransaction {
        val owner = node.info.legalIdentity.ref(0)
        val notary = notaryNode.info.notaryIdentity

        val stateA = DummyContract.SingleOwnerState(Random().nextInt(), owner.party.owningKey)
        val stateB = DummyContract.SingleOwnerState(Random().nextInt(), owner.party.owningKey)
        val stateC = DummyContract.SingleOwnerState(Random().nextInt(), owner.party.owningKey)

        val tx = TransactionType.General.Builder(null).apply {
            addCommand(Command(DummyContract.Commands.Create(), owner.party.owningKey))
            addOutputState(stateA, notary, encumbrance = 2) // Encumbered by stateB
            addOutputState(stateC, notary)
            addOutputState(stateB, notary, encumbrance = 1) // Encumbered by stateC
        }
        val nodeKey = node.services.legalIdentityKey
        tx.signWith(nodeKey)
        val stx = tx.toSignedTransaction()
        node.services.recordTransactions(listOf(stx))
        return tx.toWireTransaction()
    }

    // TODO: Add more test cases once we have a general flow/service exception handling mechanism:
    //       - A participant is offline/can't be found on the network
    //       - The requesting party is not a participant
    //       - The requesting party wants to change additional state fields
    //       - Multiple states in a single "notary change" transaction
    //       - Transaction contains additional states and commands with business logic
    //       - The transaction type is not a notary change transaction at all.
}

fun issueState(node: AbstractNode, notaryNode: AbstractNode): StateAndRef<*> {
    val tx = DummyContract.generateInitial(Random().nextInt(), notaryNode.info.notaryIdentity, node.info.legalIdentity.ref(0))
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
    val tx = DummyContract.generateInitial(Random().nextInt(), notary, node.info.legalIdentity.ref(0))
    tx.setTime(Instant.now(), 30.seconds)
    val nodeKey = node.services.legalIdentityKey
    tx.signWith(nodeKey)
    val stx = tx.toSignedTransaction(false)
    node.services.recordTransactions(listOf(stx))
    return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
}
