package net.corda.core.flows

import net.corda.core.getOrThrow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TxKeyFlowTests {
    lateinit var net: MockNetwork

    @Before
    fun before() {
        net = MockNetwork(false)
    }

    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        net = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = net.createPartyNode(notaryNode.info.address, ALICE.name)
        val bobNode = net.createPartyNode(notaryNode.info.address, BOB.name)
        val alice: PartyAndCertificate = aliceNode.services.myInfo.legalIdentity
        val bob: PartyAndCertificate = bobNode.services.myInfo.legalIdentity
        aliceNode.services.identityService.registerIdentity(bob)
        aliceNode.services.identityService.registerIdentity(notaryNode.info.legalIdentity)
        bobNode.services.identityService.registerIdentity(alice)
        bobNode.services.identityService.registerIdentity(notaryNode.info.legalIdentity)

        // Run the flows
        bobNode.registerInitiatedFlow(TxKeyFlow.Provider::class.java)
        val requesterFlow = aliceNode.services.startFlow(TxKeyFlow.Requester(bob))

        // Get the results
        val actual: Map<Party, TxKeyFlow.AnonymousIdentity> = requesterFlow.resultFuture.getOrThrow()
        assertEquals(2, actual.size)
        // Verify that the generated anonymous identities do not match the well known identities
        val aliceAnonymousIdentity = actual[alice] ?: throw IllegalStateException()
        val bobAnonymousIdentity = actual[bob] ?: throw IllegalStateException()
        assertNotEquals<AbstractParty>(alice, aliceAnonymousIdentity.identity)
        assertNotEquals<AbstractParty>(bob, bobAnonymousIdentity.identity)
    }
}
