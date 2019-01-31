package net.corda.testing.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.*
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class MockNetworkTest {
    private companion object {
        private const val NODE_ID = 101
    }
    private lateinit var mockNetwork: MockNetwork

    @Before
    fun setup() {
        mockNetwork = MockNetwork(MockNetworkParameters())
    }

    @After
    fun done() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `with a started node`() {
        val unstarted = mockNetwork.createUnstartedNode(DUMMY_BANK_A_NAME, forcedID = NODE_ID)
        assertFalse(unstarted.isStarted)

        mockNetwork.startNodes()
        assertTrue(unstarted.isStarted)

        val started = unstarted.started
        assertEquals(NODE_ID, started.id)
        assertEquals(DUMMY_BANK_A_NAME, started.info.identityFromX500Name(DUMMY_BANK_A_NAME).name)
        assertFailsWith<IllegalArgumentException> { started.info.identityFromX500Name(DUMMY_BANK_B_NAME) }
    }

    @Test
    fun `with an unstarted node`() {
        val unstarted = mockNetwork.createUnstartedNode(DUMMY_BANK_A_NAME, forcedID = NODE_ID)
        val ex = assertFailsWith<IllegalStateException> { unstarted.started }
        assertThat(ex).hasMessage("Node ID=$NODE_ID is not running")
    }

    @Test
    fun installCordaService() {
        val unstarted = mockNetwork.createUnstartedNode()
        assertThat(unstarted.installCordaService(TestService::class.java)).isNotNull()
        val started = unstarted.start()
        started.registerInitiatedFlow(TestResponder::class.java)
        val future = started.startFlow(TestInitiator(started.info.singleIdentity()))
        mockNetwork.runNetwork()
        assertThat(future.getOrThrow()).isEqualTo(TestService::class.java.name)
    }

    @CordaService
    class TestService(services: AppServiceHub) : SingletonSerializeAsToken()

    @InitiatingFlow
    class TestInitiator(private val party: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            return initiateFlow(party).receive<String>().unwrap { it }
        }
    }

    @InitiatedBy(TestInitiator::class)
    class TestResponder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            otherSide.send(serviceHub.cordaService(TestService::class.java).javaClass.name)
        }
    }
}