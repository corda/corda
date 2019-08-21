package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.testing.core.*
import net.corda.testing.internal.matchers.allOf
import net.corda.testing.internal.matchers.flow.willReturn
import net.corda.testing.internal.matchers.hasOnlyEntries
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.AfterClass
import org.junit.Test
import java.security.PublicKey

class SwapIdentitiesFlowTests {
    companion object {
        private val mockNet = InternalMockNetwork(
                networkSendManuallyPumped = false,
                threadPerNode = true,
                cordappsForAllNodes = listOf(enclosedCordapp())
        )

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
        assertThat(
            aliceNode.services.startFlow(SwapIdentitiesInitiator(bob)),
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
        assertThatThrownBy { aliceNode.validateSwapIdentitiesFlow(bob, notBob, signature) }
                .isInstanceOf(SwapIdentitiesException::class.java)
                .hasMessage("Certificate subject must match counterparty's well known identity.")
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

        assertThatThrownBy { aliceNode.validateSwapIdentitiesFlow(bob, anonymousEvilBob, signature) }
                .isInstanceOf(SwapIdentitiesException::class.java)
                .hasMessage("Signature does not match the expected identity ownership assertion.")
    }

    @Test
    fun `verification rejects signature if key is right but name is wrong`() {
        val anonymousAlice = aliceNode.issueFreshKeyAndCert()
        val anonymousBob = bobNode.issueFreshKeyAndCert()
        val signature = bobNode.signSwapIdentitiesFlowData(anonymousAlice, anonymousBob.owningKey)

        assertThatThrownBy { aliceNode.validateSwapIdentitiesFlow(bob, anonymousBob, signature) }
                .isInstanceOf(SwapIdentitiesException::class.java)
                .hasMessage("Signature does not match the expected identity ownership assertion.")
    }

    //region Operations
    private fun TestStartedNode.issueFreshKeyAndCert() = database.transaction {
        services.keyManagementService.freshKeyAndCert(services.myInfo.singleIdentityAndCert(), false)
    }

    private fun TestStartedNode.signSwapIdentitiesFlowData(party: PartyAndCertificate, owningKey: PublicKey): DigitalSignature.WithKey {
        return services.keyManagementService.sign(SwapIdentitiesFlow.buildDataToSign(party), owningKey)
    }

    private fun TestStartedNode.validateSwapIdentitiesFlow(party: Party,
                                                           counterparty: PartyAndCertificate,
                                                           signature: DigitalSignature.WithKey): PartyAndCertificate {
        return SwapIdentitiesFlow.validateAndRegisterIdentity(
                services,
                party,
                counterparty,
                signature.withoutKey()
        )
    }
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

        override fun invoke(actual: AnonymousParty): MatchResult {
            return if (negated != actual.owningKey in node.services.keyManagementService.keys) {
                MatchResult.Match
            } else {
                MatchResult.Mismatch("""
                            had an owning key which was ${sayNotIf(!negated)}held by ${node.info.singleIdentity().name}
                            """.trimIndent())
            }
        }

        override fun not(): Matcher<AnonymousParty> = copy(negated=!negated)
    }

    private fun TestStartedNode.holdsOwningKey() = HoldsOwningKeyMatcher(this)
    //endregion

    @InitiatingFlow
    private class SwapIdentitiesInitiator(private val otherSide: Party) : FlowLogic<Map<Party, AnonymousParty>>() {
        @Suspendable
        override fun call(): Map<Party, AnonymousParty>  = subFlow(SwapIdentitiesFlow(initiateFlow(otherSide)))

    }

    @InitiatedBy(SwapIdentitiesInitiator::class)
    private class SwapIdentitiesResponder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(SwapIdentitiesFlow(otherSide))
        }
    }
}
