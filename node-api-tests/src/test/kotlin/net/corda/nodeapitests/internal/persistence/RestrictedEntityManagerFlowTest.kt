package net.corda.nodeapitests.internal.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.persistence.RestrictedEntityManager
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RestrictedEntityManagerFlowTest {

    private lateinit var aliceNode: StartedMockNode
    private lateinit var mockNetwork: MockNetwork

    @InitiatingFlow
    class TestIfItIsRestrictedEntityManager : FlowLogic<Boolean>() {
        @Suspendable
        override fun call() : Boolean {
            var result = false
            serviceHub.withEntityManager(){
                result = this is RestrictedEntityManager
            }
            return result
        }
    }

    @InitiatingFlow
    class TestCloseMethodIsBlocked : FlowLogic<UnsupportedOperationException?>() {
        @Suspendable
        override fun call() : UnsupportedOperationException? {
            var exception : UnsupportedOperationException? = null

            try {
                serviceHub.withEntityManager(){
                    this.close()
                }
            } catch(e : UnsupportedOperationException){
                exception = e
            }

            return exception
        }
    }

    @InitiatingFlow
    class TestJoinTransactionMethodIsBlocked : FlowLogic<UnsupportedOperationException?>() {
        @Suspendable
        override fun call() : UnsupportedOperationException? {
            var exception : UnsupportedOperationException? = null

            try {
                serviceHub.withEntityManager(){
                    this.joinTransaction()
                }
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
        assertTrue { aliceNode.startFlow(TestIfItIsRestrictedEntityManager()).get() }
        mockNetwork.runNetwork()
    }

    @Test(timeout=300_000)
    fun testMethodsAreBlocked() {
        assertNotNull({ aliceNode.startFlow(TestCloseMethodIsBlocked()).get()?.message }, "This method cannot be called via ServiceHub.withEntityManager.")
        assertNotNull({ aliceNode.startFlow(TestJoinTransactionMethodIsBlocked()).get()?.message }, "This method cannot be called via ServiceHub.withEntityManager.")
        mockNetwork.runNetwork()
    }
}