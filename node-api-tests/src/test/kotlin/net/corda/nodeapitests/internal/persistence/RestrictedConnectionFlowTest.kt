package net.corda.nodeapitests.internal.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.RestrictedConnection
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions
import org.junit.After
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
    class TestClearWarningsMethodIsBlocked : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val connection = serviceHub.jdbcSession()
            connection.clearWarnings()
        }
    }

    @After
    fun done() {
        mockNetwork.stopNodes()
    }

    @Test(timeout=300_000)
    fun `restricted connection is returned from ServiceHub#jdbcSession`() {
        mockNetwork = MockNetwork(MockNetworkParameters(listOf(enclosedCordapp().copy(targetPlatformVersion = PLATFORM_VERSION))))
        aliceNode = mockNetwork.createPartyNode(CordaX500Name("Alice", "London", "GB"))
        assertTrue { aliceNode.startFlow(TestIfItIsRestrictedConnection()).get() }
        mockNetwork.runNetwork()
    }

    @Test(timeout=300_000)
    fun `restricted methods are blocked when the target platform is the current corda version`() {
        mockNetwork = MockNetwork(MockNetworkParameters(listOf(enclosedCordapp().copy(targetPlatformVersion = PLATFORM_VERSION))))
        aliceNode = mockNetwork.createPartyNode(CordaX500Name("Alice", "London", "GB"))
        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java)
                .isThrownBy { aliceNode.startFlow(TestAutoCommitMethodIsBlocked()).getOrThrow() }
                .withMessageContaining("ServiceHub.jdbcSession.setAutoCommit is restricted and cannot be called")

        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java)
                .isThrownBy { aliceNode.startFlow(TestClearWarningsMethodIsBlocked()).getOrThrow() }
                .withMessageContaining("ServiceHub.jdbcSession.clearWarnings is restricted and cannot be called")

        mockNetwork.runNetwork()
    }

    @Test(timeout=300_000)
    fun `restricted methods are blocked when the target platform is 7`() {
        mockNetwork = MockNetwork(MockNetworkParameters(listOf(enclosedCordapp().copy(targetPlatformVersion = 7))))
        aliceNode = mockNetwork.createPartyNode(CordaX500Name("Alice", "London", "GB"))
        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java)
            .isThrownBy { aliceNode.startFlow(TestAutoCommitMethodIsBlocked()).getOrThrow() }
            .withMessageContaining("ServiceHub.jdbcSession.setAutoCommit is restricted and cannot be called")

        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java)
            .isThrownBy { aliceNode.startFlow(TestClearWarningsMethodIsBlocked()).getOrThrow() }
            .withMessageContaining("ServiceHub.jdbcSession.clearWarnings is restricted and cannot be called")

        mockNetwork.runNetwork()
    }

    @Test(timeout=300_000)
    fun `restricted methods are not blocked when the target platform is 6`() {
        mockNetwork = MockNetwork(MockNetworkParameters(listOf(enclosedCordapp().copy(targetPlatformVersion = 6))))
        aliceNode = mockNetwork.createPartyNode(CordaX500Name("Alice", "London", "GB"))
        aliceNode.startFlow(TestAutoCommitMethodIsBlocked()).getOrThrow()
        aliceNode.startFlow(TestClearWarningsMethodIsBlocked()).getOrThrow()

        mockNetwork.runNetwork()
    }
}