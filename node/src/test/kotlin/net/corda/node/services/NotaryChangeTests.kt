package net.corda.node.services

import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.StateReplacementException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.getRequiredTransaction
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.api.ServiceHubInternal
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.DescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.amqp.custom.PublicKeySerializer
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.dummyCommand
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
import java.net.URI
import java.time.Instant
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotaryChangeTests {
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
        val newState = changeNotary(state, clientNodeA, newNotaryParty)
        assertEquals(newState.state.notary, newNotaryParty)
    }

    @Test(timeout=300_000)
	fun `should change notary for a state with multiple participants`() {
        val state = issueMultiPartyState(clientNodeA, clientNodeB, oldNotaryNode, oldNotaryParty)
        val newNotary = newNotaryParty
        val flow = NotaryChangeFlow(state, newNotary)
        val future = clientNodeA.startFlow(flow)

        mockNet.runNetwork()

        val newState = future.getOrThrow()
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

        val flow = NotaryChangeFlow(state, newNotaryParty)
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
        val flow = NotaryChangeFlow(state, newNotary)
        val future = clientNodeA.startFlow(flow)
        mockNet.runNetwork()
        val newState = future.getOrThrow()
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

    @Test(timeout=300_000)
	fun `notary change and regular transactions are properly handled during resolution in longer chains`() {
        val issued = issueState(clientNodeA.services, clientA, oldNotaryParty)
        val moved = moveState(issued, clientNodeA, clientNodeB)

        // We don't to tx resolution when moving state to another node, so need to add the issue transaction manually
        // to node B. The resolution process is tested later during notarisation.
        clientNodeB.services.recordTransactions(clientNodeA.services.getRequiredTransaction(issued.ref.txhash))

        val changedNotary = changeNotary(moved, clientNodeB, newNotaryParty)
        val movedBack = moveState(changedNotary, clientNodeB, clientNodeA)
        val changedNotaryBack = changeNotary(movedBack, clientNodeA, oldNotaryParty)

        assertEquals(issued.state, changedNotaryBack.state)
    }

    private fun changeNotary(movedState: StateAndRef<DummyContract.SingleOwnerState>, node: StartedMockNode, newNotary: Party): StateAndRef<DummyContract.SingleOwnerState> {
        val flow = NotaryChangeFlow(movedState, newNotary)
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
    //       - The requesting party is not a participant
    //       - The requesting party wants to change additional state fields
    //       - Multiple states in a single "notary change" transaction
    //       - Transaction contains additional states and commands with business logic
    //       - The transaction type is not a notary change transaction at all.

    /*
    Change in NotaryChangeWireTransaction in 4.13 (https://r3-cev.atlassian.net/browse/ENT-13850)
    The NotaryChangeWireTransaction gets an extra field `requiredSigningKeys` so collect signatures
    and finality flow can be adapted to work with NotaryChangeWireTransactions as well as
    normal WireTransactions.
    These tests use pre-canned, serialized NotaryChangeWireTransactions from before the change
    and from after the change to prove that the change is forwards and backwards compatible on
    the wire.
     */

    // When regenerating the test files this needs to be set to the file system location of the resource files
    @Suppress("UNUSED")
    var localPath: URI = projectRootDir.toUri().resolve(
            "node/src/test/resources/net/corda/node/services/")

    // Read in a serialized NotaryChangeWireTransaction from 4.12 (or earlier)
    // `requiredSigningKeys` did not exist as a field when serializing
    @Test(timeout = 300_000)
    fun deserializeNotaryChangeTransactionWithoutSigners(){
        val resource = "NotaryChangeTest.transactionWithoutSigners"
        val sf = testDefaultFactory()

        val stateRef = StateRef(SecureHash.create("61A2ECDC1C54F31B7351F2C39F767D700A5658150C3E3C49F0458D487862A70D"), 0)

        // uncomment to recreate the data.
        // This has to be run on a version of Corda that does not have required signers on NotaryChangeWireTransaction
        // val networkParamsHash = SecureHash.randomSHA256()
        // val notaryChangeTx = NotaryChangeTransactionBuilder(listOf(stateRef), oldNotaryParty, newNotaryParty, networkParamsHash  ).build()
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notaryChangeTx, testSerializationContext).bytes)

        val url = NotaryChangeTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedTx = DeserializationInput(sf).deserialize(SerializedBytes<NotaryChangeWireTransaction>(sc2), testSerializationContext)

        assertEquals(1, deserializedTx.inputs.size)
        assertEquals(stateRef, deserializedTx.inputs.first())

    }

    // Read in a serialized NotaryChangeWireTransaction from 4.13+, with requiredSigningKeys
    // populated from https://github.com/corda/corda/pull/7953
    @Test(timeout = 300_000)
    fun deserializeNotaryChangeTransactionWithSigners(){
        val resource = "NotaryChangeTest.transactionWithSigners"
        val sf = testDefaultFactory()

        val stateRef = StateRef(SecureHash.create("36C3ECDC1C54F31B7351F2C39F767D700A5658150C3E3C49F0458D487862A70D"), 0)

        // uncomment to recreate the data.
        // This has to be run on a version of Corda that _has_ requiredSigningKeys on NotaryChangeWireTransaction
        // val networkParamsHash = SecureHash.randomSHA256()
        // val notaryChangeTx = NotaryChangeTransactionBuilder(listOf(stateRef), oldNotaryParty, newNotaryParty, networkParamsHash, setOf(clientA.owningKey)  ).build()
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notaryChangeTx, testSerializationContext).bytes)

        val url = NotaryChangeTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedTx = DeserializationInput(sf).deserialize(SerializedBytes<NotaryChangeWireTransaction>(sc2), testSerializationContext)

        assertEquals(1, deserializedTx.inputs.size)
        assertEquals(stateRef, deserializedTx.inputs.first())
    }

    // Read in a serialized NotaryChangeWireTransaction from 4.13+, with requiredSigningKeys
    // present, but not populated.
    @Test(timeout = 300_000)
    fun deserializeNotaryChangeTransactionWithEmptySigners(){
        val resource = "NotaryChangeTest.transactionWithEmptySigners"
        val sf = testDefaultFactory()

        val stateRef = StateRef(SecureHash.create("45D3ECDC1C54F3113351F2C39F767D700A5658150C3E3C49F0458D487862A70D"), 0)

        // uncomment to recreate the data
        // This has to be run on a version of Corda that _has_ requiredSigningKeys on NotaryChangeWireTransaction
        // val networkParamsHash = SecureHash.randomSHA256()
        // val notaryChangeTx = NotaryChangeTransactionBuilder(listOf(stateRef), oldNotaryParty, newNotaryParty, networkParamsHash, emptySet()).build()
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(notaryChangeTx, testSerializationContext).bytes)

        val url = NotaryChangeTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedTx = DeserializationInput(sf).deserialize(SerializedBytes<NotaryChangeWireTransaction>(sc2), testSerializationContext)

        assertEquals(1, deserializedTx.inputs.size)
        assertEquals(stateRef, deserializedTx.inputs.first())
    }

}

fun issueState(services: ServiceHub, nodeIdentity: Party, notaryIdentity: Party): StateAndRef<DummyContract.SingleOwnerState> {
    val tx = DummyContract.generateInitial(Random().nextInt(), notaryIdentity, nodeIdentity.ref(0))
    val stx = services.signInitialTransaction(tx)
    services.recordTransactions(stx)
    return stx.tx.outRef(0)
}

fun issueMultiPartyState(nodeA: StartedMockNode, nodeB: StartedMockNode, notaryNode: StartedMockNode, notaryIdentity: Party): StateAndRef<DummyContract.MultiOwnerState> {
    val participants = listOf(nodeA.info.singleIdentity(), nodeB.info.singleIdentity())
    val state = TransactionState(
            DummyContract.MultiOwnerState(0, participants),
            DummyContract.PROGRAM_ID, notaryIdentity)
    val tx = TransactionBuilder(notary = notaryIdentity).withItems(state, dummyCommand(participants.first().owningKey))
    val signedByA = nodeA.services.signInitialTransaction(tx)
    val signedByAB = nodeB.services.addSignature(signedByA)
    val stx = notaryNode.services.addSignature(signedByAB, notaryIdentity.owningKey)
    nodeA.services.recordTransactions(stx)
    nodeB.services.recordTransactions(stx)
    return stx.tx.outRef(0)
}

fun issueInvalidState(services: ServiceHub, identity: Party, notary: Party): StateAndRef<DummyContract.SingleOwnerState> {
    val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
    tx.setTimeWindow(Instant.now(), 30.seconds)
    val stx = services.signInitialTransaction(tx)
    (services as ServiceHubInternal).recordTransactions(StatesToRecord.ONLY_RELEVANT, listOf(stx), disableSignatureVerification = true)
    return stx.tx.outRef(0)
}

@JvmOverloads
fun testDefaultFactory(descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                               DefaultDescriptorBasedSerializerRegistry()) =
        SerializerFactoryBuilder.build(
                AllWhitelist,
                ClassCarpenterImpl(AllWhitelist, ClassLoader.getSystemClassLoader()),
                descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry).also { it.register(PublicKeySerializer) }

val serializationProperties: MutableMap<Any, Any> = mutableMapOf()

val testSerializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        deserializationClassLoader = ClassLoader.getSystemClassLoader(),
        whitelist = AllWhitelist,
        properties = serializationProperties,
        objectReferencesEnabled = false,
        useCase = SerializationContext.UseCase.Testing,
        encoding = null)