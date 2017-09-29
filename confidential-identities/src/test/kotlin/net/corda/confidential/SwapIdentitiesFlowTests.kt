package net.corda.confidential

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import org.junit.Test
import kotlin.test.*

class SwapIdentitiesFlowTests {
    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        val mockNet = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)
        val alice = notaryNode.services.networkMapCache.getPeerByLegalName(ALICE_NAME)!!
        val bob = notaryNode.services.networkMapCache.getPeerByLegalName(BOB_NAME)!!

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

    /**
     * Check that flow is actually validating the name on the certificate presented by the counterparty.
     */
    @Test
    fun `verifies identity name`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        val mockNet = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode(DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)
        val bob: Party = bobNode.services.myInfo.chooseIdentity()
        val notBob = notaryNode.database.transaction {
            notaryNode.services.keyManagementService.freshKeyAndCert(notaryNode.services.myInfo.chooseIdentityAndCert(), false)
        }
        val sigData = SwapIdentitiesFlow.buildDataToSign(notBob)
        val signature = notaryNode.services.keyManagementService.sign(sigData, notBob.owningKey)
        assertFailsWith<SwapIdentitiesException>("Certificate subject must match counterparty's well known identity.") {
            SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob, notBob, signature.withoutKey())
        }

        mockNet.stopNodes()
    }

    /**
     * Check that flow is actually validating its the signature presented by the counterparty.
     */
    @Test
    fun `verifies signature`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        val mockNet = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode(DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(ALICE.name)
        val bobNode = mockNet.createPartyNode(BOB.name)
        val bob: Party = bobNode.services.myInfo.chooseIdentity()
        // Check that the wrong signature is rejected
        notaryNode.database.transaction {
            notaryNode.services.keyManagementService.freshKeyAndCert(notaryNode.services.myInfo.chooseIdentityAndCert(), false)
        }.let { anonymousNotary ->
            val sigData = SwapIdentitiesFlow.buildDataToSign(anonymousNotary)
            val signature = notaryNode.services.keyManagementService.sign(sigData, anonymousNotary.owningKey)
            assertFailsWith<SwapIdentitiesException>("Signature does not match the given identity and nonce") {
                SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob, anonymousNotary, signature.withoutKey())
            }
        }
        // Check that the right signing key, but wrong identity is rejected
        val anonymousAlice = aliceNode.database.transaction {
            aliceNode.services.keyManagementService.freshKeyAndCert(aliceNode.services.myInfo.chooseIdentityAndCert(), false)
        }
        bobNode.database.transaction {
            bobNode.services.keyManagementService.freshKeyAndCert(bobNode.services.myInfo.chooseIdentityAndCert(), false)
        }.let { anonymousBob ->
            val sigData = SwapIdentitiesFlow.buildDataToSign(anonymousAlice)
            val signature = bobNode.services.keyManagementService.sign(sigData, anonymousBob.owningKey)
            assertFailsWith<SwapIdentitiesException>("Signature does not match the given identity and nonce.") {
                SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob, anonymousBob, signature.withoutKey())
            }
        }

        mockNet.stopNodes()
    }
}
