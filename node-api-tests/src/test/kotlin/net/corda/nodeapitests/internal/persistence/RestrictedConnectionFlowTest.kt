package net.corda.nodeapitests.internal.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.persistence.RestrictedConnection
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RestrictedConnectionFlowTest {
    private lateinit var aliceNode: StartedMockNode
    private lateinit var mockNetwork: MockNetwork

    @InitiatingFlow
    class TestIfItIsRestrictedConnection : FlowLogic<Boolean>() {
        @Suspendable
        override fun call() : Boolean {
            val connection = serviceHub.jdbcSession()
            return connection is RestrictedConnection
        }
    }

    @InitiatingFlow
    class TestAutoCommitMethodIsBlocked : FlowLogic<UnsupportedOperationException?>() {
        @Suspendable
        override fun call() : UnsupportedOperationException? {
            val connection = serviceHub.jdbcSession()
            var exception : UnsupportedOperationException? = null

            try {
                connection.autoCommit = true
            } catch(e : UnsupportedOperationException){
                exception = e
            }

            return exception
        }
    }

    @InitiatingFlow
    class TestCloseMethodIsBlocked : FlowLogic<UnsupportedOperationException?>() {
        @Suspendable
        override fun call() : UnsupportedOperationException? {
            val connection = serviceHub.jdbcSession()
            var exception : UnsupportedOperationException? = null

            try {
                connection.close()
            } catch(e : UnsupportedOperationException){
                exception = e
            }

            return exception
        }
    }

    @Before
    fun init() {
        mockNetwork = MockNetwork(MockNetworkParameters())
        aliceNode = mockNetwork.createPartyNode(CordaX500Name("Alice", "London", "GB"))
    }

    @After
    fun done() {
        mockNetwork.stopNodes()
    }

    @Test(timeout=300_000)
    fun testIfItIsRestrictedConnection() {
        assertTrue { aliceNode.startFlow(TestIfItIsRestrictedConnection()).get() }
        mockNetwork.runNetwork()
    }

    @Test(timeout=300_000)
    fun testMethodsAreBlocked() {
        assertNotNull({ aliceNode.startFlow(TestAutoCommitMethodIsBlocked()).get()?.message }, "This method cannot be called via ServiceHub.jdbcSession.")
        assertNotNull({ aliceNode.startFlow(TestCloseMethodIsBlocked()).get()?.message }, "This method cannot be called via ServiceHub.jdbcSession.")
        mockNetwork.runNetwork()
    }
}