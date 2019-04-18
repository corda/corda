package flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.flow.RequestKeyFlow
import net.corda.confidential.flow.RequestKeyFlow.Companion.tracker
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test

class RequestKeyFlowTests {

    private lateinit var network: MockNetwork
    private lateinit var partyA: StartedMockNode
    private lateinit var user: Party
    private lateinit var serviceHub: ServiceHub
    private val progressTracker: ProgressTracker = tracker()

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var notary: Party


    @Before
    fun before() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = InternalMockNetwork(
                cordappPackages = listOf(
                        //TODO add corda-node stuff here?
                        "com."
                ),
                cordappsForAllNodes = FINANCE_CORDAPPS,
                networkSendManuallyPumped = false,
                threadPerNode = true)

        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `blah`() {
        // TODO copy IdentitySyncFlowTests ??
        aliceNode.services.startFlow(Initiator(alice)).resultFuture.getOrThrow()
    }

    @InitiatingFlow
    class Initiator(private val otherSide: Party) : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val session = initiateFlow(otherSide)
            subFlow(RequestKeyFlow(setOf(session), otherSide, progressTracker!!))
            // Wait for the counterparty to indicate they're done
            return session.receive<Boolean>().unwrap { it }
        }
    }
}