package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SwapIdentitiesFlowTests {
    @Test
    fun `swap anonymous identities`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        val mockNet = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val alice: Party = aliceNode.services.myInfo.legalIdentity
        val bob: Party = bobNode.services.myInfo.legalIdentity
        aliceNode.services.identityService.verifyAndRegisterIdentity(bobNode.info.legalIdentityAndCert)
        aliceNode.services.identityService.verifyAndRegisterIdentity(notaryNode.info.legalIdentityAndCert)
        bobNode.services.identityService.verifyAndRegisterIdentity(aliceNode.info.legalIdentityAndCert)
        bobNode.services.identityService.verifyAndRegisterIdentity(notaryNode.info.legalIdentityAndCert)

        bobNode.registerInitiatedFlow(Acceptor::class.java)

        // Run the flows
        val requesterFlow = aliceNode.services.startFlow(Initiator(bob))

        // Get the results
        val result = requesterFlow.resultFuture.getOrThrow()
        // Verify that the generated anonymous identities do not match the well known identities
        val aliceAnonymousIdentity = result.ourIdentity
        val bobAnonymousIdentity = result.theirIdentity
        assertNotEquals<AbstractParty>(alice, aliceAnonymousIdentity)
        assertNotEquals<AbstractParty>(bob, bobAnonymousIdentity)

        // Verify that the anonymous identities look sane
        assertEquals(alice.name, aliceNode.services.identityService.partyFromAnonymous(aliceAnonymousIdentity)!!.name)
        assertEquals(bob.name, bobNode.services.identityService.partyFromAnonymous(bobAnonymousIdentity)!!.name)

        // Verify that the nodes have the right anonymous identities
        assertTrue { aliceAnonymousIdentity.owningKey in aliceNode.services.keyManagementService.keys }
        assertTrue { bobAnonymousIdentity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { aliceAnonymousIdentity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { bobAnonymousIdentity.owningKey in aliceNode.services.keyManagementService.keys }

        mockNet.stopNodes()
    }

    @InitiatingFlow
    private class Initiator(val otherParty: Party) : FlowLogic<SwapIdentitiesFlow.Result>() {
        @Suspendable
        override fun call(): SwapIdentitiesFlow.Result = subFlow(SwapIdentitiesFlow(otherParty))
    }

    @InitiatedBy(Initiator::class)
    private class Acceptor(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(SwapIdentitiesFlow(otherParty))
        }
    }
}
