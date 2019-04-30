package flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.flow.ShareKeyFlow
import net.corda.confidential.flow.ShareKeyFlowHandler
import net.corda.confidential.service.SignedPublicKey
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.util.*

class ShareKeyFlowTests {
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

        aliceNode.registerInitiatedFlow(ShareKeyResponder::class.java)
        bobNode.registerInitiatedFlow(ShareKeyResponder::class.java)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `share new key with another party `() {
        // Alice creates a new key and shares it with Bob
        aliceNode.services.startFlow(ShareKeyInitiator(bob, UUID.randomUUID())).resultFuture
        mockNet.runNetwork()

        // Alice has her own key and the newly generated key
        val aliceKeys = aliceNode.services.keyManagementService.keys
        assertThat(aliceKeys).hasSize(2)

        val bobService = bobNode.services.identityService
        val aliceService = bobNode.services.identityService
        val aliceGeneratedKey = aliceKeys.last()

        // Bob should be able to resolve the generated key as it has been shared with him
        val resolvedParty = bobNode.services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(aliceGeneratedKey))
        assertThat(resolvedParty).isEqualTo(alice)
    }
}

@InitiatingFlow
private class ShareKeyInitiator(private val otherParty: Party, private val uuid: UUID) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ShareKeyFlow(initiateFlow(otherParty), uuid))
    }
}

@InitiatedBy(ShareKeyInitiator::class)
private class ShareKeyResponder(private val otherSession: FlowSession) : FlowLogic<SignedPublicKey>() {
    @Suspendable
    override fun call() : SignedPublicKey{
        return subFlow(ShareKeyFlowHandler(otherSession))
    }
}

