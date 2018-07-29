package net.corda.node.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowRegistrationTest {

    lateinit var mockNetwork: MockNetwork
    lateinit var node: StartedMockNode

    @Before
    fun setup() {
        // no cordapps scanned so it can be tested in isolation
        mockNetwork = MockNetwork(emptyList())
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    @Test(expected = IllegalStateException::class)
    fun `startup fails when two flows initiated by the same flow are registered`() {
        node = mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("node", "Reading", "GB")))
        // register the same flow twice to invoke the error without causing errors in other tests
        node.registerInitiatedFlow(Responder::class.java)
        node.registerInitiatedFlow(Responder::class.java)
    }

    @Test
    fun `a single initiated flow can be registered without error`() {
        node = mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("node", "Reading", "GB")))
        node.registerInitiatedFlow(Responder::class.java)
    }
}

@InitiatingFlow
class Initiator : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
    }
}

@InitiatedBy(Initiator::class)
private class Responder(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
    }
}