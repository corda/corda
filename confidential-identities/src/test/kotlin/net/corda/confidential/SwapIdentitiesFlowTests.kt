package net.corda.confidential

import net.corda.core.identity.*
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.*
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class SwapIdentitiesFlowTests {
    private lateinit var mockNet: InternalMockNetwork

    @Before
    fun setup() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = InternalMockNetwork(networkSendManuallyPumped = false, threadPerNode = true)
    }

    @Test
    fun `issue key`() {
        // Set up values we'll need
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val alice = aliceNode.info.singleIdentity()
        val bob = bobNode.services.myInfo.singleIdentity()

        // Run the flows
        val requesterFlow = aliceNode.services.startFlow(SwapIdentitiesFlow(bob)).resultFuture

        // Get the results
        val actual: Map<Party, AnonymousParty> = requesterFlow.getOrThrow().toMap()
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
        // Set up values we'll need
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        val bob: Party = bobNode.services.myInfo.singleIdentity()
        val notBob = charlieNode.database.transaction {
            charlieNode.services.keyManagementService.freshKeyAndCert(charlieNode.services.myInfo.singleIdentityAndCert(), false)
        }
        val sigData = SwapIdentitiesFlow.buildDataToSign(notBob)
        val signature = charlieNode.services.keyManagementService.sign(sigData, notBob.owningKey)
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
        // Set up values we'll need
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val alice: PartyAndCertificate = aliceNode.info.singleIdentityAndCert()
        val bob: PartyAndCertificate = bobNode.info.singleIdentityAndCert()
        // Check that the right name but wrong key is rejected
        val evilBobNode = mockNet.createPartyNode(BOB_NAME)
        val evilBob = evilBobNode.info.singleIdentityAndCert()
        evilBobNode.database.transaction {
            val anonymousEvilBob = evilBobNode.services.keyManagementService.freshKeyAndCert(evilBob, false)
            val sigData = SwapIdentitiesFlow.buildDataToSign(evilBob)
            val signature = evilBobNode.services.keyManagementService.sign(sigData, anonymousEvilBob.owningKey)
            assertFailsWith<SwapIdentitiesException>("Signature does not match the given identity and nonce") {
                SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob.party, anonymousEvilBob, signature.withoutKey())
            }
        }
        // Check that the right signing key, but wrong identity is rejected
        val anonymousAlice: PartyAndCertificate = aliceNode.database.transaction {
            aliceNode.services.keyManagementService.freshKeyAndCert(alice, false)
        }
        bobNode.database.transaction {
            bobNode.services.keyManagementService.freshKeyAndCert(bob, false)
        }.let { anonymousBob ->
            val sigData = SwapIdentitiesFlow.buildDataToSign(anonymousAlice)
            val signature = bobNode.services.keyManagementService.sign(sigData, anonymousBob.owningKey)
            assertFailsWith<SwapIdentitiesException>("Signature does not match the given identity and nonce.") {
                SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob.party, anonymousBob, signature.withoutKey())
            }
        }

        mockNet.stopNodes()
    }
}
