package net.corda.flows

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TransactionKeyFlowTests {
    lateinit var mockNet: MockNetwork

    @Before
    fun before() {
        mockNet = MockNetwork(false)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val alice: Party = aliceNode.services.myInfo.legalIdentity
        val bob: Party = bobNode.services.myInfo.legalIdentity
        aliceNode.services.identityService.registerIdentity(bobNode.info.legalIdentityAndCert)
        aliceNode.services.identityService.registerIdentity(notaryNode.info.legalIdentityAndCert)
        bobNode.services.identityService.registerIdentity(aliceNode.info.legalIdentityAndCert)
        bobNode.services.identityService.registerIdentity(notaryNode.info.legalIdentityAndCert)

        // Run the flows
        val requesterFlow = aliceNode.services.startFlow(TransactionKeyFlow(bob))

        // Get the results
        val actual: Map<Party, AnonymisedIdentity> = requesterFlow.resultFuture.getOrThrow().toMap()
        assertEquals(2, actual.size)
        // Verify that the generated anonymous identities do not match the well known identities
        val aliceAnonymousIdentity = actual[alice] ?: throw IllegalStateException()
        val bobAnonymousIdentity = actual[bob] ?: throw IllegalStateException()
        assertNotEquals<AbstractParty>(alice, aliceAnonymousIdentity.identity)
        assertNotEquals<AbstractParty>(bob, bobAnonymousIdentity.identity)

        // Verify that the anonymous identities look sane
        assertEquals(alice.name, aliceAnonymousIdentity.certificate.subject)
        assertEquals(bob.name, bobAnonymousIdentity.certificate.subject)

        // Verify that the nodes have the right anonymous identities
        assertTrue { aliceAnonymousIdentity.identity.owningKey in aliceNode.services.keyManagementService.keys }
        assertTrue { bobAnonymousIdentity.identity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { aliceAnonymousIdentity.identity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { bobAnonymousIdentity.identity.owningKey in aliceNode.services.keyManagementService.keys }
    }
}
