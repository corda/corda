package net.corda.confidential

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getOrThrow
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.chooseIdentity
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
        val alice: Party = aliceNode.services.myInfo.chooseIdentity()
        val bob: Party = bobNode.services.myInfo.chooseIdentity()

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
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val bob: Party = bobNode.services.myInfo.legalIdentity
        val nonce = ByteArray(1) { 0 }
        val notBob = notaryNode.database.transaction {
            notaryNode.services.keyManagementService.freshKeyAndCert(notaryNode.services.myInfo.legalIdentityAndCert, false)
        }
        val notBobBytes = SerializedBytes<PartyAndCertificate>(notBob.serialize().bytes)
        val sigData = SwapIdentitiesFlow.buildDataToSign(notBobBytes, nonce)
        val signature = notaryNode.services.keyManagementService.sign(sigData, notBob.owningKey)
        assertFailsWith<SwapIdentitiesException>("Certificate subject must match counterparty's well known identity.") {
            SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob, notBobBytes, nonce, signature.bytes)
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
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val bob: Party = bobNode.services.myInfo.legalIdentity
        val nonce = ByteArray(1) { 0 }
        val wrongNonce = ByteArray(1) { Byte.MAX_VALUE }
        // Check that the right signing key but the wrong nonce is rejected
        bobNode.database.transaction {
            bobNode.services.keyManagementService.freshKeyAndCert(bobNode.services.myInfo.legalIdentityAndCert, false)
        }.let { anonymousBob ->
            val anonymousBobBytes = SerializedBytes<PartyAndCertificate>(anonymousBob.serialize().bytes)
            val sigData = SwapIdentitiesFlow.buildDataToSign(anonymousBobBytes, wrongNonce)
            val signature = bobNode.services.keyManagementService.sign(sigData, anonymousBob.owningKey)
            assertFailsWith<SwapIdentitiesException>("Signature does not match the given identity and nonce.") {
                SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob, anonymousBobBytes, nonce, signature.bytes)
            }
        }
        // Check that the wrong signature with the correct nonce is rejected
        notaryNode.database.transaction {
            notaryNode.services.keyManagementService.freshKeyAndCert(notaryNode.services.myInfo.legalIdentityAndCert, false)
        }.let { anonymousNotary ->
            val anonymousNotaryBytes = SerializedBytes<PartyAndCertificate>(anonymousNotary.serialize().bytes)
            val sigData = SwapIdentitiesFlow.buildDataToSign(anonymousNotaryBytes, nonce)
            val signature = notaryNode.services.keyManagementService.sign(sigData, anonymousNotary.owningKey)
            assertFailsWith<SwapIdentitiesException>("Signature does not match the given identity and nonce") {
                SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob, anonymousNotaryBytes, nonce, signature.bytes)
            }
        }
        // Check that the right signing key, right nonce but wrong identity is rejected
        val anonymousAlice = aliceNode.database.transaction {
            aliceNode.services.keyManagementService.freshKeyAndCert(aliceNode.services.myInfo.legalIdentityAndCert, false)
        }
        bobNode.database.transaction {
            bobNode.services.keyManagementService.freshKeyAndCert(bobNode.services.myInfo.legalIdentityAndCert, false)
        }.let { anonymousBob ->
            val anonymousAliceBytes = SerializedBytes<PartyAndCertificate>(anonymousAlice.serialize().bytes)
            val anonymousBobBytes = SerializedBytes<PartyAndCertificate>(anonymousBob.serialize().bytes)
            val sigData = SwapIdentitiesFlow.buildDataToSign(anonymousAliceBytes, nonce)
            val signature = bobNode.services.keyManagementService.sign(sigData, anonymousBob.owningKey)
            assertFailsWith<SwapIdentitiesException>("Signature does not match the given identity and nonce.") {
                SwapIdentitiesFlow.validateAndRegisterIdentity(aliceNode.services.identityService, bob, anonymousBobBytes, nonce, signature.bytes)
            }
        }

        mockNet.stopNodes()
    }
}
