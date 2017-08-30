package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdentitySyncFlowTests {
    lateinit var mockNet: MockNetwork

    @Before
    fun before() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = MockNetwork(networkSendManuallyPumped = false, threadPerNode = true)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `sync confidential identities`() {
        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val alice: Party = aliceNode.services.myInfo.legalIdentity
        val bob: Party = bobNode.services.myInfo.legalIdentity
        aliceNode.services.identityService.verifyAndRegisterIdentity(bobNode.info.legalIdentityAndCert)
        aliceNode.services.identityService.verifyAndRegisterIdentity(notaryNode.info.legalIdentityAndCert)
        bobNode.services.identityService.verifyAndRegisterIdentity(aliceNode.info.legalIdentityAndCert)
        bobNode.services.identityService.verifyAndRegisterIdentity(notaryNode.info.legalIdentityAndCert)
        bobNode.registerInitiatedFlow(Receive::class.java)

        // Alice issues then pays some cash to a new confidential identity that Bob doesn't know about
        val anonymous = true
        val ref = OpaqueBytes.of(0x01)
        val issueFlow = aliceNode.services.startFlow(CashIssueAndPaymentFlow(1000.DOLLARS, ref, alice, anonymous, notaryNode.services.myInfo.notaryIdentity))
        val issueTx = issueFlow.resultFuture.getOrThrow().stx
        val confidentialIdentity = issueTx.tx.outputs.map { it.data }.filterIsInstance<Cash.State>().single().owner
        assertNull(bobNode.services.identityService.partyFromAnonymous(confidentialIdentity))

        // Run the flow to sync up the identities
        aliceNode.services.startFlow(Initiator(bob, issueTx.tx)).resultFuture.getOrThrow()
        val expected = aliceNode.services.identityService.partyFromAnonymous(confidentialIdentity)
        val actual = bobNode.services.identityService.partyFromAnonymous(confidentialIdentity)
        assertEquals(expected, actual)
    }

    /**
     * Very lightweight wrapping flow to trigger the counterparty flow that receives the identities.
     */
    @InitiatingFlow
    class Initiator(val otherSide: Party, val tx: WireTransaction): FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            subFlow(IdentitySyncFlow.Send(otherSide, tx))
            // Wait for the counterparty to indicate they're done
            return receive<Boolean>(otherSide).unwrap { it }
        }
    }

    @InitiatedBy(IdentitySyncFlowTests.Initiator::class)
    class Receive(val otherSide: Party): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(IdentitySyncFlow.Receive(otherSide))
            // Notify the initiator that we've finished syncing
            send(otherSide, true)
        }
    }
}