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
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThatIllegalStateException
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
        mockNetwork = MockNetwork(emptyList())
        initiator = mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("initiator", "Reading", "GB")))
        responder = mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("responder", "Reading", "GB")))
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `startup fails when two flows initiated by the same flow are registered`() {
        // register the same flow twice to invoke the error without causing errors in other tests
        responder.registerInitiatedFlow(Responder::class.java)
        assertThatIllegalStateException().isThrownBy { responder.registerInitiatedFlow(Responder::class.java) }
    }

    @Test
    fun `a single initiated flow can be registered without error`() {
        responder.registerInitiatedFlow(Responder::class.java)
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
private class Responder(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
        session.send("What's up")
    }
}