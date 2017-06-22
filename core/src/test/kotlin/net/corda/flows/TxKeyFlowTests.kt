package net.corda.flows

import net.corda.core.getOrThrow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TxKeyFlowTests {
    lateinit var mockNet: MockNetwork

    @Before
    fun before() {
        mockNet = MockNetwork(false)
    }

    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.info.address, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.info.address, BOB.name)
        val alice: Party = aliceNode.services.myInfo.legalIdentity
        val bob: Party = bobNode.services.myInfo.legalIdentity
        aliceNode.services.identityService.registerIdentity(bobNode.info.legalIdentityAndCert)
        aliceNode.services.identityService.registerIdentity(notaryNode.info.legalIdentityAndCert)
        bobNode.services.identityService.registerIdentity(aliceNode.info.legalIdentityAndCert)
        bobNode.services.identityService.registerIdentity(notaryNode.info.legalIdentityAndCert)

        // Run the flows
        val requesterFlow = aliceNode.services.startFlow(TxKeyFlow.Requester(bob))

        // Get the results
        val actual: Map<Party, AnonymisedIdentity> = requesterFlow.resultFuture.getOrThrow()
        assertEquals(2, actual.size)
        // Verify that the generated anonymous identities do not match the well known identities
        val aliceAnonymousIdentity = actual[alice] ?: throw IllegalStateException()
        val bobAnonymousIdentity = actual[bob] ?: throw IllegalStateException()
        assertNotEquals<AbstractParty>(alice, aliceAnonymousIdentity.identity)
        assertNotEquals<AbstractParty>(bob, bobAnonymousIdentity.identity)
    }
}
