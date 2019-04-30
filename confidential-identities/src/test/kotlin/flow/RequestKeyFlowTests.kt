package flow

import co.paralleluniverse.fibers.Suspendable
import junit.framework.Assert.assertNull
import net.corda.confidential.flow.RequestKeyFlow
import net.corda.confidential.flow.RequestKeyFlowHandler
import net.corda.confidential.service.SignedPublicKey
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import sun.security.x509.UniqueIdentity
import java.util.*

class RequestKeyFlowTests {

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = FINANCE_CORDAPPS,
                networkSendManuallyPumped = false,
                threadPerNode = false)

        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()

        aliceNode.registerInitiatedFlow(RequestKeyResponder::class.java)
        bobNode.registerInitiatedFlow(RequestKeyResponder::class.java)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `request new key from another party`() {
        // Alice requests that bob generates a new key for an account
        val keyForBob = aliceNode.services.startFlow(RequestKeyInitiator(bob, UUID.randomUUID())).resultFuture
        mockNet.runNetwork()

        val bobResults = keyForBob.getOrThrow().publicKeyToPartyMap.filter { it.value == bob }

        // Bob has the newly generated key as well as the owning key
        val bobKeys = aliceNode.services.keyManagementService.keys
        val aliceKeys = aliceNode.services.keyManagementService.keys
        assertThat(bobKeys).hasSize(2)
        assertThat(aliceKeys).hasSize(1)

        assertThat(bobNode.services.keyManagementService.keys).contains(bobResults.keys.first())

        val resolvedBobParty = aliceNode.services.identityService.wellKnownPartyFromAnonymous(bobResults.values.first())
        assertThat(resolvedBobParty).isEqualTo(bob)
    }

    @Test
    fun `verify flow exception`(){
        //TODO need to figure out how to force a duplicate key to be used
    }

    @InitiatingFlow
    private class RequestKeyInitiator(private val otherParty: Party, private val uuid: UUID) : FlowLogic<SignedPublicKey>() {
        @Suspendable
        override fun call(): SignedPublicKey {
            return subFlow(RequestKeyFlow(initiateFlow(otherParty), otherParty, uuid))
        }
    }

    @InitiatedBy(RequestKeyInitiator::class)
    private class RequestKeyResponder(private val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(RequestKeyFlowHandler(otherSession))
        }
    }
}