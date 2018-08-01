/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.confidential

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import net.corda.core.identity.*
import net.corda.testing.core.*
import net.corda.testing.internal.matchers.allOf
import net.corda.testing.internal.matchers.flow.willReturn
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import kotlin.test.*
import com.natpryce.hamkrest.assertion.assert
import net.corda.core.crypto.DigitalSignature
import net.corda.testing.internal.matchers.hasOnlyEntries
import net.corda.testing.node.internal.TestStartedNode
import org.junit.AfterClass
import java.security.PublicKey

class SwapIdentitiesFlowTests {
    companion object {
        private val mockNet = InternalMockNetwork(networkSendManuallyPumped = false, threadPerNode = true)

        @AfterClass
        @JvmStatic
        fun tearDown() = mockNet.stopNodes()
    }

    private val aliceNode = mockNet.createPartyNode(makeUnique(ALICE_NAME))
    private val bobNode = mockNet.createPartyNode(makeUnique(BOB_NAME))
    private val charlieNode = mockNet.createPartyNode(makeUnique(CHARLIE_NAME))
    private val alice = aliceNode.info.singleIdentity()
    private val bob = bobNode.info.singleIdentity()

    @Test
    fun `issue key`() {
        assert.that(
            aliceNode.services.startFlow(SwapIdentitiesFlow(bob)),
            willReturn(
                hasOnlyEntries(
                    alice to allOf(
                        !equalTo<AbstractParty>(alice),
                        aliceNode.resolvesToWellKnownParty(alice),
                        aliceNode.holdsOwningKey(),
                        !bobNode.holdsOwningKey()
                    ),
                    bob to allOf(
                        !equalTo<AbstractParty>(bob),
                        bobNode.resolvesToWellKnownParty(bob),
                        bobNode.holdsOwningKey(),
                        !aliceNode.holdsOwningKey()
                    )
                )
            )
        )
    }

    /**
     * Check that flow is actually validating the name on the certificate presented by the counterparty.
     */
    @Test
    fun `verifies identity name`() {
        val notBob = charlieNode.issueFreshKeyAndCert()
        val signature = charlieNode.signSwapIdentitiesFlowData(notBob, notBob.owningKey)
        assertFailsWith<SwapIdentitiesException>(
            "Certificate subject must match counterparty's well known identity.") {
            aliceNode.validateSwapIdentitiesFlow(bob, notBob, signature)
        }
    }

    /**
     * Check that flow is actually validating its the signature presented by the counterparty.
     */
    @Test
    fun `verification rejects signature if name is right but key is wrong`() {
        val evilBobNode = mockNet.createPartyNode(bobNode.info.singleIdentity().name)
        val evilBob = evilBobNode.info.singleIdentityAndCert()
        val anonymousEvilBob = evilBobNode.issueFreshKeyAndCert()
        val signature = evilBobNode.signSwapIdentitiesFlowData(evilBob, anonymousEvilBob.owningKey)

        assertFailsWith<SwapIdentitiesException>(
                "Signature does not match the given identity and nonce") {
            aliceNode.validateSwapIdentitiesFlow(bob, anonymousEvilBob, signature)
        }
    }

    @Test
    fun `verification rejects signature if key is right but name is wrong`() {
        val anonymousAlice = aliceNode.issueFreshKeyAndCert()
        val anonymousBob = bobNode.issueFreshKeyAndCert()
        val signature = bobNode.signSwapIdentitiesFlowData(anonymousAlice, anonymousBob.owningKey)

        assertFailsWith<SwapIdentitiesException>(
                "Signature does not match the given identity and nonce.") {
                aliceNode.validateSwapIdentitiesFlow(bob, anonymousBob, signature)
        }
    }

    //region Operations
    private fun TestStartedNode.issueFreshKeyAndCert() = database.transaction {
        services.keyManagementService.freshKeyAndCert(services.myInfo.singleIdentityAndCert(), false)
    }

    private fun TestStartedNode.signSwapIdentitiesFlowData(party: PartyAndCertificate, owningKey: PublicKey) =
            services.keyManagementService.sign(
                    SwapIdentitiesFlow.buildDataToSign(party),
                    owningKey)

    private fun TestStartedNode.validateSwapIdentitiesFlow(
            party: Party,
            counterparty: PartyAndCertificate,
            signature: DigitalSignature.WithKey) =
            SwapIdentitiesFlow.validateAndRegisterIdentity(
                    services.identityService,
                    party,
                    counterparty,
                    signature.withoutKey()
            )
    //endregion

    //region Matchers
    private fun TestStartedNode.resolvesToWellKnownParty(party: Party) = object : Matcher<AnonymousParty> {
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

    private data class HoldsOwningKeyMatcher(val node: TestStartedNode, val negated: Boolean = false) : Matcher<AnonymousParty> {
        private fun sayNotIf(negation: Boolean) = if (negation) { "not " } else { "" }

        override val description =
                "has an owning key which is ${sayNotIf(negated)}held by ${node.info.singleIdentity().name}"

        override fun invoke(actual: AnonymousParty) =
                if (negated != actual.owningKey in node.services.keyManagementService.keys) {
                    MatchResult.Match
                } else {
                    MatchResult.Mismatch("""
                    had an owning key which was ${sayNotIf(!negated)}held by ${node.info.singleIdentity().name}
                    """.trimIndent())
                }

        override fun not(): Matcher<AnonymousParty> {
            return copy(negated=!negated)
        }
    }

    private fun TestStartedNode.holdsOwningKey() = HoldsOwningKeyMatcher(this)
    //endregion

}
