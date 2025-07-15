package net.corda.node.services

import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.MoveNotaryFlow
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.StateReplacementException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.keyService
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Assert.assertFalse
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

    private fun <T: ContractState> moveNotary(states: List<StateAndRef<T>>, node: StartedMockNode, newNotary: Party): List<StateAndRef<T>> {
        val flow = MoveNotaryFlow(states, newNotary)
        val future = node.startFlow(flow)
        mockNet.runNetwork()

        return future.getOrThrow()
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
    //       - The requesting party wants to change additional state fields
    //       - Multiple states in a single "notary change" transaction
    //       - Transaction contains additional states and commands with business logic
    //       - The transaction type is not a notary change transaction at all.


    @Test(timeout = 314_159)
    fun `moving notary for two states from the same tx works`(){
        val inputStates = issueTwoStateTx(clientNodeA.services, clientA, oldNotaryParty).outRefsOfType<DummyContract.SingleOwnerState>()
        val newStates  = moveNotary(inputStates, clientNodeA, newNotaryParty)

        assertEquals(2, newStates.size)
        assertEquals(newStates.first().state.notary, newNotaryParty)
        assertEquals(inputStates.first().state.data.magicNumber, newStates.first().state.data.magicNumber)
        assertEquals(newStates.last().state.notary, newNotaryParty)
        assertEquals(inputStates.last().state.data.magicNumber, newStates.last().state.data.magicNumber)

    }


    // moving notary for two states from different tx works
    @Test( timeout = 300_000)
    fun `should change notary for two states with single participant from different txs`() {
        val state1 = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state1.state.notary, oldNotaryParty)
        val state2 = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state2.state.notary, oldNotaryParty)

        val newStates = moveNotary(listOf(state1, state2), clientNodeA, newNotaryParty)
        assertEquals(2, newStates.size)
        assertEquals(newStates.first().state.notary, newNotaryParty)
        assertEquals(state1.state.data.magicNumber, newStates.first().state.data.magicNumber)
        assertEquals(newStates.last().state.notary, newNotaryParty)
        assertEquals(state2.state.data.magicNumber, newStates.last().state.data.magicNumber)
    }

    // moving notary for one encumbered state A and an unencumbered state B returns A', B', encumbrances
    @Test( timeout = 300_000)
    fun `Adding encumbrances should not change the order of inputs vs outputs`() {
        val issueTx = issueEncumberedState(clientNodeA.services, clientA, oldNotaryParty)
        val state1 = StateAndRef<DummyContract.SingleOwnerState>(uncheckedCast(issueTx.outputs.first()), StateRef(issueTx.id, 0))
        assertEquals(state1.state.notary, oldNotaryParty)
        val state2 = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state2.state.notary, oldNotaryParty)

        val newStates = moveNotary(listOf(state1, state2), clientNodeA, newNotaryParty)
        assertEquals(2, newStates.size)
        assertEquals(newStates.first().state.notary, newNotaryParty)
        assertEquals(state1.state.data.magicNumber, newStates.first().state.data.magicNumber)
        assertEquals(newStates.last().state.notary, newNotaryParty)
        assertEquals(state2.state.data.magicNumber, newStates.last().state.data.magicNumber)
    }

    // Adding more than one encumbered state does not mess up the ordering
    @Test( timeout = 300_000)
    fun `Adding more than one encumbered state does not mess up the ordering`() {
        val issueTx = issueEncumberedState(clientNodeA.services, clientA, oldNotaryParty)
        val state1 = StateAndRef<DummyContract.SingleOwnerState>(uncheckedCast(issueTx.outputs.first()), StateRef(issueTx.id, 0))
        val state3 = StateAndRef<DummyContract.SingleOwnerState>(uncheckedCast(issueTx.outputs[2]), StateRef(issueTx.id, 2))
        assertEquals(state1.state.notary, oldNotaryParty)
        assertEquals(state3.state.notary, oldNotaryParty)
        val state2 = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state2.state.notary, oldNotaryParty)

        val newStates = moveNotary(listOf(state1, state2, state3), clientNodeA, newNotaryParty)
        assertEquals(3, newStates.size)
        assertEquals(newStates.first().state.notary, newNotaryParty)
        assertEquals(state1.state.data.magicNumber, newStates.first().state.data.magicNumber)
        assertEquals(newStates[1].state.notary, newNotaryParty)
        assertEquals(state2.state.data.magicNumber, newStates[1].state.data.magicNumber)
        assertEquals(newStates[2].state.notary, newNotaryParty)
        assertEquals(state3.state.data.magicNumber, newStates[2].state.data.magicNumber)
    }


    // moving a state with encumbrances that require different signers works
    @Test(timeout = 300_000)
    fun `should get correct signers for multi party encumbrance if signed by all`() {
        val issueTx = issueMultiPartyEncumberedState(clientNodeA, clientNodeB, oldNotaryNode, oldNotaryParty)
        val state = StateAndRef(issueTx.outputs.first(), StateRef(issueTx.id, 0))

        val newNotary = newNotaryParty
        val flow = MoveNotaryFlow(listOf(state), newNotary)

        val future = clientNodeA.startFlow(flow)
        mockNet.runNetwork()

        val newState = future.getOrThrow().single()
        assertEquals(newState.state.notary, newNotary)

        val recordedTx = clientNodeA.services.getRequiredTransaction(newState.ref.txhash)
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

    @Test(timeout = 300_000)
    fun `should get correct signers for multi party encumbrance if signed by one`() {
        val issueTx = issueMultiPartyEncumberedState(clientNodeA, clientNodeB, oldNotaryNode, oldNotaryParty)

        // use the third state, only signed by nodeA
        val state = StateAndRef(issueTx.outputs[2], StateRef(issueTx.id, 2))
        assertTrue(state.state.data.participants.contains(clientNodeA.info.singleIdentity()),
                "Expected ${clientNodeA.info.singleIdentity()}, got ${state.state.data.participants}")

        assertFalse(state.state.data.participants.contains(clientNodeB.info.singleIdentity()))
        val newNotary = newNotaryParty

        val flow = MoveNotaryFlow(listOf(state), newNotary)

        val future = clientNodeA.startFlow(flow)
        mockNet.runNetwork()

        val newState = future.getOrThrow().single()

        assertEquals(newState.state.notary, newNotary)

        val recordedTx = clientNodeA.services.getRequiredTransaction(newState.ref.txhash)
        assertEquals(3, recordedTx.sigs.size)

        val recordedSigners = recordedTx.sigs.map { it.by }
        assertTrue { recordedSigners.contains(clientNodeA.info.singleIdentity().owningKey) }
        assertTrue { recordedSigners.contains(clientNodeB.info.singleIdentity().owningKey) }

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

    @Test( timeout = 300_000)
    fun `moving a state to the same notary fails`() {
        val state1 = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state1.state.notary, oldNotaryParty)
        assertThatThrownBy { MoveNotaryFlow(listOf(state1), oldNotaryParty) }.hasMessage("The new notary cannot be the same as the old notary")
    }


    @Test(timeout = 299_327)
    fun `moving states that are on different notaries fails`() {
        val state1 = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state1.state.notary, oldNotaryParty)
        val state2 = issueState(clientNodeA.services, clientA, newNotaryParty)
        assertEquals(state2.state.notary, newNotaryParty)
        assertThatThrownBy { MoveNotaryFlow(listOf(state1, state2), newNotaryParty) }.hasMessage("All input states must be on the same notary")
    }

    @Test( timeout = 300_000)
    fun `Invoking notary change without states fails`() {
        assertThatThrownBy { MoveNotaryFlow(listOf<StateAndRef<ContractState>>(), newNotaryParty) }
                .hasMessage("Notary change flow must receive at least one state to work on")
    }

    @Test( timeout = 300_000)
    fun `moving a state we're not a participant in fails`(){
        val state1 = issueState(clientNodeA.services, clientA, oldNotaryParty)
        assertEquals(state1.state.notary, oldNotaryParty)
        val flow = MoveNotaryFlow(listOf(state1), newNotaryParty)
        val future = clientNodeB.startFlow(flow)
        mockNet.runNetwork()
        assertThatThrownBy{ future.get() }
                .hasMessage("java.lang.IllegalArgumentException: Cannot request move for state we are not a participant in")
    }

    @Test( timeout = 300_000)
    fun `cannot change notary to non-existent notary`(){
        val state = issueState(clientNodeA.services, clientA, oldNotaryParty)

        val nonExistentNotary = Party.create( CordaX500Name("Pirates", "Concarneau", "FR"), keyService.freshKey())

        val flow = MoveNotaryFlow(listOf(state), nonExistentNotary)
        val future = clientNodeA.startFlow(flow)
        mockNet.runNetwork()

       assertThatThrownBy {  future.get() }.hasMessageContaining("The output notary ${nonExistentNotary.description()} is not whitelisted in the attached network parameters")
    }

    // works with confidential identities
}

fun issueMultiPartyEncumberedState(nodeA: StartedMockNode, nodeB: StartedMockNode, notaryNode: StartedMockNode, notaryIdentity: Party): WireTransaction {

    val participants = listOf(nodeA.info.singleIdentity(), nodeB.info.singleIdentity())
    val stateA = DummyContract.MultiOwnerState(0, participants)
    val stateB = DummyContract.SingleOwnerState(Random().nextInt(), nodeA.info.singleIdentity())
    val stateC = DummyContract.SingleOwnerState(Random().nextInt(), nodeB.info.singleIdentity())

    val tx = TransactionBuilder(notary = notaryIdentity).apply {
        addCommand(Command(DummyContract.Commands.Create(), nodeA.info.singleIdentity().owningKey))
        addOutputState(stateA, DummyContract.PROGRAM_ID, notaryIdentity, encumbrance = 2) // Encumbered by stateB
        addOutputState(stateC, DummyContract.PROGRAM_ID, notaryIdentity, encumbrance = 0) // Encumbered by stateA
        addOutputState(stateB, DummyContract.PROGRAM_ID, notaryIdentity, encumbrance = 1) // Encumbered by stateC
    }

    val signedByA = nodeA.services.signInitialTransaction(tx)
    val signedByAB = nodeB.services.addSignature(signedByA)
    val stx = notaryNode.services.addSignature(signedByAB, notaryIdentity.owningKey)

    nodeA.services.recordTransactions(stx)
    nodeB.services.recordTransactions(stx)

    return stx.tx
}


fun issueTwoStateTx( services: ServiceHub, participant: Party, notary: Party ) : WireTransaction {
    val stateA = DummyContract.SingleOwnerState(Random().nextInt(), participant)
    val stateB = DummyContract.SingleOwnerState(Random().nextInt(), participant)

    val tx = TransactionBuilder(notary = notary).apply {
        addCommand(Command(DummyContract.Commands.Create(), participant.owningKey))
        addOutputState(stateA, DummyContract.PROGRAM_ID, notary)
        addOutputState(stateB, DummyContract.PROGRAM_ID, notary)
    }
    val stx = services.signInitialTransaction(tx)
    services.recordTransactions(stx)
    return stx.tx
}