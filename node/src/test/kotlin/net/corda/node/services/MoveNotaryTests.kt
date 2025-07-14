package net.corda.node.services

import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.MoveNotaryFlow
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.StateReplacementException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveNotaryTests {
    private val oldNotaryName = CordaX500Name("Old Notary", "Zurich", "CH")
    private val newNotaryName = CordaX500Name("New Notary", "Zurich", "CH")

    private lateinit var mockNet: MockNetwork
    private lateinit var oldNotaryNode: StartedMockNode
    private lateinit var clientNodeA: StartedMockNode
    private lateinit var clientNodeB: StartedMockNode
    private lateinit var newNotaryParty: Party
    private lateinit var oldNotaryParty: Party
    private lateinit var clientA: Party

    @Before
    fun setUp() {
        mockNet = MockNetwork(MockNetworkParameters(
                notarySpecs = listOf(MockNetworkNotarySpec(oldNotaryName), MockNetworkNotarySpec(newNotaryName)),
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP)
        ))
        clientNodeA = mockNet.createNode(MockNodeParameters(legalName = ALICE_NAME))
        clientNodeB = mockNet.createNode(MockNodeParameters(legalName = BOB_NAME))
        clientA = clientNodeA.info.singleIdentity()
        oldNotaryNode = mockNet.notaryNodes[0]
        oldNotaryParty = clientNodeA.services.networkMapCache.getNotary(oldNotaryName)!!
        newNotaryParty = clientNodeA.services.networkMapCache.getNotary(newNotaryName)!!
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
	fun `should change notary for a state with single participant`() {
        val state = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state.state.notary, oldNotaryParty)
        val newState = moveNotaryForSingleState(state, clientNodeA, newNotaryParty)
        assertEquals(newState.state.notary, newNotaryParty)
    }

    @Test(timeout=300_000)
	fun `should change notary for a state with multiple participants`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB, oldNotaryNode, oldNotaryParty)
        val newNotary = newNotaryParty
        val flow = MoveNotaryFlow(listOf(state), newNotary)
        val future = clientNodeA.startFlow(flow)

        mockNet.runNetwork()

        val newState = future.getOrThrow().single()
        assertEquals(newState.state.notary, newNotary)
        val loadedStateA = clientNodeA.services.loadState(newState.ref)
        val loadedStateB = clientNodeB.services.loadState(newState.ref)
        assertEquals(loadedStateA, loadedStateB)
    }

    // TODO: Re-enable the test when parameter currentness checks are in place, ENT-2666.
    @Test(timeout=300_000)
@Ignore
    fun `should throw when a participant refuses to change Notary`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB, oldNotaryNode, oldNotaryParty)

        val flow = MoveNotaryFlow(listOf(state), newNotaryParty)
        val future = clientNodeA.startFlow(flow)

        mockNet.runNetwork()

        assertThatExceptionOfType(StateReplacementException::class.java).isThrownBy {
            future.getOrThrow()
        }
    }

    @Test(timeout=300_000)
	fun `should not break encumbrance links`() {
        val issueTx = issueEncumberedState(clientNodeA.services, clientA, oldNotaryParty)

        val state = StateAndRef(issueTx.outputs.first(), StateRef(issueTx.id, 0))
        val newNotary = newNotaryParty
        val flow = MoveNotaryFlow(listOf(state), newNotary)
        val future = clientNodeA.startFlow(flow)
        mockNet.runNetwork()
        val newStates = future.getOrThrow()
        assertTrue(newStates.all{ it.state.notary == newNotary})

        val recordedTx = clientNodeA.services.getRequiredTransaction(newStates.first().ref.txhash)
        val notaryChangeTx = recordedTx.resolveNotaryChangeTransaction(clientNodeA.services)

        // Check that all encumbrances have been propagated to the outputs
        val originalOutputs = issueTx.outputStates
        val newOutputs = notaryChangeTx.outputStates
        assertTrue(originalOutputs.size == newOutputs.size && originalOutputs.containsAll(newOutputs))

        // Check if encumbrance linking between states has not changed.
        val originalLinkedStates = issueTx.outputs.asSequence().filter { it.encumbrance != null }
                .map { Pair(it.data, issueTx.outputs[it.encumbrance!!].data) }.toSet()
        val notaryChangeLinkedStates = notaryChangeTx.outputs.asSequence().filter { it.encumbrance != null }
                .map { Pair(it.data, notaryChangeTx.outputs[it.encumbrance!!].data) }.toSet()

        assertTrue { originalLinkedStates.size == notaryChangeLinkedStates.size && originalLinkedStates.containsAll(notaryChangeLinkedStates) }
    }

    @Test(timeout=300_000)
	fun `notary change and regular transactions are properly handled during resolution in longer chains`() {
        val issued = issueState(clientNodeA.services, clientA, oldNotaryParty)
        val moved = moveState(issued, clientNodeA, clientNodeB)

        // We don't to tx resolution when moving state to another node, so need to add the issue transaction manually
        // to node B. The resolution process is tested later during notarisation.
        clientNodeB.services.recordTransactions(clientNodeA.services.getRequiredTransaction(issued.ref.txhash))

        val changedNotary = moveNotaryForSingleState(moved, clientNodeB, newNotaryParty)
        val movedBack = moveState(changedNotary, clientNodeB, clientNodeA)
        val changedNotaryBack = moveNotaryForSingleState(movedBack, clientNodeA, oldNotaryParty)

        assertEquals(issued.state, changedNotaryBack.state)
    }

    private fun moveNotaryForSingleState(movedState: StateAndRef<DummyContract.SingleOwnerState>, node: StartedMockNode, newNotary: Party): StateAndRef<DummyContract.SingleOwnerState> {
        val flow = MoveNotaryFlow(listOf(movedState), newNotary)
        val future = node.startFlow(flow)
        mockNet.runNetwork()

        return future.getOrThrow().first()

    }

    private fun moveState(state: StateAndRef<DummyContract.SingleOwnerState>, fromNode: StartedMockNode, toNode: StartedMockNode): StateAndRef<DummyContract.SingleOwnerState> {
        val tx = DummyContract.move(state, toNode.info.singleIdentity())
        val stx = fromNode.services.signInitialTransaction(tx)

        val notaryFlow = NotaryFlow.Client(stx)
        val future = fromNode.startFlow(notaryFlow)
        mockNet.runNetwork()

        val notarySignature = future.getOrThrow()
        val finalTransaction = stx + notarySignature

        fromNode.services.recordTransactions(finalTransaction)
        toNode.services.recordTransactions(finalTransaction)

        return finalTransaction.tx.outRef(0)
    }

    private fun issueEncumberedState(services: ServiceHub, nodeIdentity: Party, notaryIdentity: Party): WireTransaction {
        val owner = nodeIdentity.ref(0)
        val stateA = DummyContract.SingleOwnerState(Random().nextInt(), owner.party)
        val stateB = DummyContract.SingleOwnerState(Random().nextInt(), owner.party)
        val stateC = DummyContract.SingleOwnerState(Random().nextInt(), owner.party)

        // Ensure encumbrances form a cycle.
        val tx = TransactionBuilder(null).apply {
            addCommand(Command(DummyContract.Commands.Create(), owner.party.owningKey))
            addOutputState(stateA, DummyContract.PROGRAM_ID, notaryIdentity, encumbrance = 2) // Encumbered by stateB
            addOutputState(stateC, DummyContract.PROGRAM_ID, notaryIdentity, encumbrance = 0) // Encumbered by stateA
            addOutputState(stateB, DummyContract.PROGRAM_ID, notaryIdentity, encumbrance = 1) // Encumbered by stateC
        }
        val stx = services.signInitialTransaction(tx)
        services.recordTransactions(stx)
        return tx.toWireTransaction(services)
    }

    // TODO: Add more test cases once we have a general flow/service exception handling mechanism:
    //       - A participant is offline/can't be found on the network
    //       - The requesting party is not a participant
    //       - The requesting party wants to change additional state fields
    //       - Multiple states in a single "notary change" transaction
    //       - Transaction contains additional states and commands with business logic
    //       - The transaction type is not a notary change transaction at all.
}

