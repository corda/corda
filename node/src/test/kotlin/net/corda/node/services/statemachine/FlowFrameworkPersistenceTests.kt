package net.corda.node.services.statemachine

import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.persistence.checkpoints
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.flows.registerCordappFlowFactory
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowFrameworkPersistenceTests {
    companion object {
        init {
            LogHelper.setLevel("+net.corda.flow")
        }
    }

    private lateinit var mockNet: InternalMockNetwork
    private val receivedSessionMessages = ArrayList<SessionTransfer>()
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var notaryIdentity: Party
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var aliceFlowManager: MockNodeFlowManager
    private lateinit var bobFlowManager: MockNodeFlowManager

    @Before
    fun start() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()
        )
        aliceFlowManager = MockNodeFlowManager()
        bobFlowManager = MockNodeFlowManager()

        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME, flowManager = aliceFlowManager))
        bobNode = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, flowManager = bobFlowManager))

        receivedSessionMessagesObservable().forEach { receivedSessionMessages += it }

        // Extract identities
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notaryIdentity = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        receivedSessionMessages.clear()
    }

    @Test
    fun `newly added flow is preserved on restart`() {
        aliceNode.services.startFlow(NoOpFlow(nonTerminating = true))
        aliceNode.internals.acceptableLiveFiberCountOnStop = 1
        val restoredFlow = aliceNode.restartAndGetRestoredFlow<NoOpFlow>()
        assertThat(restoredFlow.flowStarted).isTrue()
    }

    @Test
    fun `flow restarted just after receiving payload`() {
        bobNode.registerCordappFlowFactory(SendFlow::class) { InitiatedReceiveFlow(it)
                .nonTerminating() }
        aliceNode.services.startFlow(SendFlow("Hello", bob))

        // We push through just enough messages to get only the payload sent
        bobNode.pumpReceive()
        bobNode.internals.disableDBCloseOnStop()
        bobNode.internals.acceptableLiveFiberCountOnStop = 1
        bobNode.dispose()
        mockNet.runNetwork()
        val restoredFlow = bobNode.restartAndGetRestoredFlow<InitiatedReceiveFlow>()
        assertThat(restoredFlow.receivedPayloads[0]).isEqualTo("Hello")
    }

    @Test
    fun `flow loaded from checkpoint will respond to messages from before start`() {
        aliceNode.registerCordappFlowFactory(ReceiveFlow::class) { InitiatedSendFlow("Hello", it) }
        bobNode.services.startFlow(ReceiveFlow(alice).nonTerminating()) // Prepare checkpointed receive flow
        val restoredFlow = bobNode.restartAndGetRestoredFlow<ReceiveFlow>()
        assertThat(restoredFlow.receivedPayloads[0]).isEqualTo("Hello")
    }

    @Ignore("Some changes in startup order make this test's assumptions fail.")
    @Test
    fun `flow with send will resend on interrupted restart`() {
        val payload = random63BitValue()
        val payload2 = random63BitValue()

        var sentCount = 0
        mockNet.messagingNetwork.sentMessages.toSessionTransfers().filter { it.isPayloadTransfer }.forEach { sentCount++ }
        val charlieNode = mockNet.createNode(InternalMockNodeParameters(legalName = CHARLIE_NAME))
        val secondFlow = charlieNode.registerCordappFlowFactory(PingPongFlow::class) { PingPongFlow(it, payload2) }
        mockNet.runNetwork()
        val charlie = charlieNode.info.singleIdentity()

        // Kick off first send and receive
        bobNode.services.startFlow(PingPongFlow(charlie, payload))
        bobNode.database.transaction {
            assertEquals(1, bobNode.internals.checkpointStorage.checkpoints().size)
        }
        // Make sure the add() has finished initial processing.
        bobNode.internals.disableDBCloseOnStop()
        // Restart node and thus reload the checkpoint and resend the message with same UUID
        bobNode.dispose()
        bobNode.database.transaction {
            assertEquals(1, bobNode.internals.checkpointStorage.checkpoints().size) // confirm checkpoint
            bobNode.services.networkMapCache.clearNetworkMapCache()
        }
        val node2b = mockNet.createNode(InternalMockNodeParameters(bobNode.internals.id))
        bobNode.internals.manuallyCloseDB()
        val (firstAgain, fut1) = node2b.getSingleFlow<PingPongFlow>()
        // Run the network which will also fire up the second flow. First message should get deduped. So message data stays in sync.
        mockNet.runNetwork()
        fut1.getOrThrow()

        val receivedCount = receivedSessionMessages.count { it.isPayloadTransfer }
        // Check flows completed cleanly and didn't get out of phase
        assertEquals(4, receivedCount, "Flow should have exchanged 4 unique messages")// Two messages each way
        // can't give a precise value as every addMessageHandler re-runs the undelivered messages
        assertTrue(sentCount > receivedCount, "Node restart should have retransmitted messages")
        node2b.database.transaction {
            assertEquals(0, node2b.internals.checkpointStorage.checkpoints().size, "Checkpoints left after restored flow should have ended")
        }
        charlieNode.database.transaction {
            assertEquals(0, charlieNode.internals.checkpointStorage.checkpoints().size, "Checkpoints left after restored flow should have ended")
        }
        assertEquals(payload2, firstAgain.receivedPayload, "Received payload does not match the first value on Node 3")
        assertEquals(payload2 + 1, firstAgain.receivedPayload2, "Received payload does not match the expected second value on Node 3")
        assertEquals(payload, secondFlow.getOrThrow().receivedPayload, "Received payload does not match the (restarted) first value on Node 2")
        assertEquals(payload + 1, secondFlow.getOrThrow().receivedPayload2, "Received payload does not match the expected second value on Node 2")
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //region Helpers

    private inline fun <reified P : FlowLogic<*>> TestStartedNode.restartAndGetRestoredFlow(): P {
        val newNode = mockNet.restartNode(this)
        newNode.internals.acceptableLiveFiberCountOnStop = 1
        mockNet.runNetwork()
        return newNode.getSingleFlow<P>().first
    }

    private fun receivedSessionMessagesObservable(): Observable<SessionTransfer> {
        return mockNet.messagingNetwork.receivedMessages.toSessionTransfers()
    }

    //endregion Helpers
}
