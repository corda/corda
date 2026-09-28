package net.corda.finance.flows

import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.trackBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.*
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CashPaymentFlowTests {
    private lateinit var mockNet: MockNetwork
    private val initialBalance = 2000.DOLLARS
    private val ref = OpaqueBytes.of(0x01)
    private lateinit var bankOfCordaNode: StartedMockNode
    private lateinit var bankOfCorda: Party
    private lateinit var aliceNode: StartedMockNode

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(servicePeerAllocationStrategy = RoundRobin(), cordappsForAllNodes = FINANCE_CORDAPPS))
        bankOfCordaNode = mockNet.createPartyNode(BOC_NAME)
        bankOfCorda = bankOfCordaNode.info.identityFromX500Name(BOC_NAME)
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val future = bankOfCordaNode.startFlow(CashIssueFlow(initialBalance, ref, mockNet.defaultNotaryIdentity))
        future.getOrThrow()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
	fun `pay some cash`() {
        val payTo = aliceNode.info.singleIdentity()
        val expectedPayment = 500.DOLLARS
        val expectedChange = 1500.DOLLARS

        // Register for vault updates
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val (_, vaultUpdatesBoc) = bankOfCordaNode.services.vaultService.trackBy<Cash.State>(criteria)
        val (_, vaultUpdatesBankClient) = aliceNode.services.vaultService.trackBy<Cash.State>(criteria)

        val future = bankOfCordaNode.startFlow(CashPaymentFlow(expectedPayment, payTo))
        mockNet.runNetwork()
        future.getOrThrow()

        // Check Bank of Corda vault updates - we take in some issued cash and split it into $500 to the notary
        // and $1,500 back to us, so we expect to consume one state, produce one state for our own vault
        vaultUpdatesBoc.expectEvents {
            expect { (consumed, produced) ->
                assertThat(consumed).hasSize(1)
                assertThat(produced).hasSize(1)
                val changeState = produced.single().state.data
                assertEquals(expectedChange.`issued by`(bankOfCorda.ref(ref)), changeState.amount)
            }
        }

        // Check notary node vault updates
        vaultUpdatesBankClient.expectEvents {
            expect { (consumed, produced) ->
                assertThat(consumed).isEmpty()
                assertThat(produced).hasSize(1)
                val paymentState = produced.single().state.data
                assertEquals(expectedPayment.`issued by`(bankOfCorda.ref(ref)), paymentState.amount)
            }
        }
    }

    @Test(timeout=300_000)
	fun `pay some cash sends the change to the well known identity when anonymous is false`() {
        // As anonymous is false, the change should be spent back to Bank of Corda's well known identity rather than
        // to a freshly generated confidential (anonymous) identity.
        payAndAssertChangeOwner(anonymous = false) { changeState ->
            assertEquals(bankOfCorda, changeState.owner)
            assertEquals(bankOfCorda.owningKey, changeState.owner.owningKey)
        }
    }

    @Test(timeout=300_000)
	fun `pay some cash sends the change to an anonymous identity when anonymous is true`() {
        // As anonymous is true, the change should be spent to a freshly generated confidential (anonymous) identity
        // which is not the well known identity, but still resolves back to it.
        payAndAssertChangeOwner(anonymous = true) { changeState ->
            assertNotEquals(bankOfCorda, changeState.owner)
            assertNotEquals(bankOfCorda.owningKey, changeState.owner.owningKey)
            assertEquals(bankOfCorda, bankOfCordaNode.services.identityService.wellKnownPartyFromAnonymous(changeState.owner))
        }
    }

    /**
     * Pays $500 of the initial $2,000 balance to Alice and asserts the resulting $1,500 change state, delegating
     * the ownership check of that change state to [assertChangeOwner] so each test can verify the [anonymous] behaviour.
     */
    private fun payAndAssertChangeOwner(anonymous: Boolean, assertChangeOwner: (Cash.State) -> Unit) {
        val recipient = aliceNode.info.singleIdentity()
        val expectedPayment = 500.DOLLARS
        val expectedChange = 1500.DOLLARS

        // Register for vault updates
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val (_, vaultUpdatesTrackerBankOfCorda) = bankOfCordaNode.services.vaultService.trackBy<Cash.State>(criteria)

        // Make a payment
        val future = bankOfCordaNode.startFlow(CashPaymentFlow(expectedPayment, recipient, anonymous = anonymous))
        mockNet.runNetwork()
        future.getOrThrow()

        vaultUpdatesTrackerBankOfCorda.expectEvents {
            expect { (consumed, produced) ->
                assertThat(consumed).hasSize(1)
                assertThat(produced).hasSize(1)
                val changeState = produced.single().state.data
                assertEquals(expectedChange.`issued by`(bankOfCorda.ref(ref)), changeState.amount)
                assertChangeOwner(changeState)
            }
        }
    }

    @Test(timeout=300_000)
	fun `pay more than we have`() {
        val payTo = aliceNode.info.singleIdentity()
        val expected = 4000.DOLLARS
        val future = bankOfCordaNode.startFlow(CashPaymentFlow(expected,
                payTo))
        mockNet.runNetwork()
        assertFailsWith<CashException> {
            future.getOrThrow()
        }
    }

    @Test(timeout=300_000)
	fun `pay zero cash`() {
        val payTo = aliceNode.info.singleIdentity()
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.startFlow(CashPaymentFlow(expected,
                payTo))
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            future.getOrThrow()
        }
    }
}
