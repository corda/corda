package net.corda.nodeapitests.internal.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.RestrictedConnection
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
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
    class TestAutoCommitMethodIsBlocked : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val connection = serviceHub.jdbcSession()
            connection.autoCommit = true
        }
    }

    @InitiatingFlow
    class TestCloseMethodIsBlocked : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val connection = serviceHub.jdbcSession()
            connection.close()
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
        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java)
                .isThrownBy { aliceNode.startFlow(TestAutoCommitMethodIsBlocked()).getOrThrow() }
                .withMessageContaining("This method cannot be called via ServiceHub.jdbcSession.")

        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java)
                .isThrownBy { aliceNode.startFlow(TestCloseMethodIsBlocked()).getOrThrow() }
                .withMessageContaining("This method cannot be called via ServiceHub.jdbcSession.")

        mockNetwork.runNetwork()
    }
}