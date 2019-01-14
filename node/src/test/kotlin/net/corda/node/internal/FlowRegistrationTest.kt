package net.corda.node.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull

class FlowRegistrationTest {

    lateinit var mockNetwork: MockNetwork
    lateinit var initiator: StartedMockNode
    lateinit var responder: StartedMockNode

    @Before
    fun setup() {
        // no cordapps scanned so it can be tested in isolation
        mockNetwork = MockNetwork(MockNetworkParameters())
        initiator = mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("initiator", "Reading", "GB")))
        responder = mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("responder", "Reading", "GB")))
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `succeeds when a subclass of a flow initiated by the same flow is registered`() {
        // register the same flow twice to invoke the error without causing errors in other tests
        responder.registerInitiatedFlow(Responder1::class.java)
        responder.registerInitiatedFlow(Responder1Subclassed::class.java)
    }

    @Test
    fun `a single initiated flow can be registered without error`() {
        responder.registerInitiatedFlow(Responder1::class.java)
        val result = initiator.startFlow(Initiator(responder.info.singleIdentity()))
        mockNetwork.runNetwork()
        assertNotNull(result.get())
    }
}

@InitiatingFlow
class Initiator(val party: Party) : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        return initiateFlow(party).sendAndReceive<String>("Hello there").unwrap { it }
    }
}

@InitiatedBy(Initiator::class)
private open class Responder1(val session: FlowSession) : FlowLogic<Unit>() {
    open fun getPayload(): String {
        return "whats up"
    }

    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
        session.send("What's up")
    }
}

@InitiatedBy(Initiator::class)
private open class Responder2(val session: FlowSession) : FlowLogic<Unit>() {
    open fun getPayload(): String {
        return "whats up"
    }

    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
        session.send("What's up")
    }
}

@InitiatedBy(Initiator::class)
private class Responder1Subclassed(session: FlowSession) : Responder1(session) {

    override fun getPayload(): String {
        return "im subclassed! that's what's up!"
    }

    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
        session.send("What's up")
    }
}