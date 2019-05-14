package net.corda.confidential.identities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.SignedKeyToPartyMapping
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShareKeyFlowTests {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var charlieNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var charlie: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = FINANCE_CORDAPPS,
                networkSendManuallyPumped = false,
                threadPerNode = true)

        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = bobNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()

        aliceNode.registerInitiatedFlow(ShareKeyResponder::class.java)
        bobNode.registerInitiatedFlow(ShareKeyResponder::class.java)
        charlieNode.registerInitiatedFlow(ShareKeyResponder::class.java)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `share new key with another party `() {
        // Alice creates a new key and shares it with Bob
        aliceNode.services.startFlow(ShareKeyInitiator(bob, UUID.randomUUID())).resultFuture.getOrThrow()

        // Alice has her own key and the newly generated key
        val aliceKeys = aliceNode.services.keyManagementService.keys
        assertThat(aliceKeys).hasSize(2)

        val aliceGeneratedKey = aliceKeys.last()

        // Bob should be able to resolve the generated key as it has been shared with him
        val resolvedParty = bobNode.services.identityService.wellKnownPartyFromAnonymous(AnonymousParty(aliceGeneratedKey))
        assertThat(resolvedParty).isEqualTo(alice)
    }

    @Test
    fun `verify a known key with another party`() {
        // Charlie issues then pays some cash to a new confidential identity
        val anonymous = true
        val ref = OpaqueBytes.of(0x01)
        val issueFlow = charlieNode.services.startFlow(CashIssueAndPaymentFlow(1000.DOLLARS, ref, charlie, anonymous, notary))
        val issueTx = issueFlow.resultFuture.getOrThrow().stx
        val confidentialIdentity = issueTx.tx.outputs.map { it.data }.filterIsInstance<Cash.State>().single().owner

        // Bob knows nothing of the CI before we share the key
        assertNull(bobNode.database.transaction { bobNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity) })
        bobNode.services.startFlow(ShareKeyFlowWrapper(charlie, confidentialIdentity.owningKey)).resultFuture.getOrThrow()

        val expected = charlieNode.database.transaction {
            charlieNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity)
        }
        val actual = bobNode.database.transaction {
            bobNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity)
        }
        assertEquals(expected, actual)
    }
}

@InitiatingFlow
private class ShareKeyInitiator(private val otherParty: Party, private val uuid: UUID) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
            subFlow(ShareKeyFlow(initiateFlow(otherParty), uuid))
        }
    }

@InitiatedBy(ShareKeyInitiator::class)
private class ShareKeyResponder(private val otherSession: FlowSession) : FlowLogic<SignedKeyToPartyMapping>() {
    @Suspendable
    override fun call() : SignedKeyToPartyMapping {
        return subFlow(ShareKeyFlowHandler(otherSession))
    }
}

