package net.corda.confidential

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import net.corda.core.identity.*
import net.corda.testing.core.*
import net.corda.testing.internal.matchers.allOf
import net.corda.testing.internal.matchers.flow.willReturn
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import kotlin.test.*
import com.natpryce.hamkrest.assertion.assert
import net.corda.testing.internal.matchers.hasEntries
import net.corda.testing.internal.matchers.hasEntrySetSize
import net.corda.testing.internal.matchers.hasOnlyEntries
import net.corda.testing.node.internal.TestStartedNode
import org.junit.AfterClass

class SwapIdentitiesFlowTests {
    companion object {
        private val mockNet = InternalMockNetwork(networkSendManuallyPumped = false, threadPerNode = true)

        @AfterClass
        @JvmStatic
        fun teardown() = mockNet.stopNodes()
    }

    private val aliceNode = mockNet.createPartyNode(randomise(ALICE_NAME))
    private val bobNode = mockNet.createPartyNode(randomise(BOB_NAME))
    private val alice = aliceNode.info.singleIdentity()
    private val bob = bobNode.services.myInfo.singleIdentity()

    fun TestStartedNode.resolvesToWellKnownParty(party: Party) = object : Matcher<AnonymousParty> {
        override val description = """
            is resolved by "${this@resolvesToWellKnownParty.info.singleIdentity().name}" to well-known party "${party.name}"
        """.trimIndent()

        override fun invoke(actual: AnonymousParty): MatchResult {
            val resolvedName = services.identityService.wellKnownPartyFromAnonymous(actual)!!.name
            return if (resolvedName == party.name) {
                MatchResult.Match
            } else {
                MatchResult.Mismatch("was resolved to $resolvedName")
            }
        }
    }

    @Test
    fun `issue key`() {
        assert.that(
            aliceNode.services.startFlow(SwapIdentitiesFlow(bob)),
            willReturn(
                hasOnlyEntries(
                    alice to allOf(
                        !equalTo<AbstractParty>(alice),
                        aliceNode.resolvesToWellKnownParty(alice)
                    ),
                    bob to allOf(
                        !equalTo<AbstractParty>(bob),
                        bobNode.resolvesToWellKnownParty(alice)
                    )
                )
            )
        )

        // Get the results
        //val actual: Map<Party, AnonymousParty> = requesterFlow.getOrThrow().toMap()


        // Verify that the nodes have the right anonymous identities
        /*
        assertTrue { aliceAnonymousIdentity.owningKey in aliceNode.services.keyManagementService.keys }
        assertTrue { bobAnonymousIdentity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { aliceAnonymousIdentity.owningKey in bobNode.services.keyManagementService.keys }
        assertFalse { bobAnonymousIdentity.owningKey in aliceNode.services.keyManagementService.keys }
        */
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
    }
}
