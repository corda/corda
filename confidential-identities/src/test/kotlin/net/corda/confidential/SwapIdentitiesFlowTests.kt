package net.corda.confidential

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SwapIdentitiesFlowTests {
    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        val mockNet = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val alice: Party = aliceNode.services.myInfo.chooseIdentity()
        val bob: Party = bobNode.services.myInfo.chooseIdentity()
        mockNet.registerIdentities()

        // Run the flows
        val requesterFlow = aliceNode.services.startFlow(SwapIdentitiesFlow(bob))

        // Get the results
        val actual: Map<Party, AnonymousParty> = requesterFlow.resultFuture.getOrThrow().toMap()
        assertEquals(2, actual.size)
        // Verify that the generated anonymous identities do not match the well known identities
        val aliceAnonymousIdentity = actual[alice] ?: throw IllegalStateException()
        val bobAnonymousIdentity = actual[bob] ?: throw IllegalStateException()
        assertNotEquals<AbstractParty>(alice, aliceAnonymousIdentity)
        assertNotEquals<AbstractParty>(bob, bobAnonymousIdentity)

        // Verify that the anonymous identities look sane
        assertEquals(alice.name, aliceNode.database.transaction { aliceNode.services.identityService.wellKnownPartyFromAnonymous(aliceAnonymousIdentity)!!.name })
        assertEquals(bob.name, bobNode.database.transaction { bobNode.services.identityService.wellKnownPartyFromAnonymous(bobAnonymousIdentity)!!.name })

        // Verify that the nodes have the right anonymous identities
        assertTrue { aliceAnonymousIdentity.owningKey in aliceNode.services.keyManagementService.keys }
        assertTrue { bobAnonymousIdentity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { aliceAnonymousIdentity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { bobAnonymousIdentity.owningKey in aliceNode.services.keyManagementService.keys }

        mockNet.stopNodes()
    }
}
