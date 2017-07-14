package net.corda.flows

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.`issued by`
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.trackBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CashPaymentFlowTests {
    private val mockNet = MockNetwork(servicePeerAllocationStrategy = RoundRobin())
    private val initialBalance = 2000.DOLLARS
    private val ref = OpaqueBytes.of(0x01)
    private lateinit var bankOfCordaNode: MockNode
    private lateinit var bankOfCorda: Party
    private lateinit var notaryNode: MockNode
    private lateinit var notary: Party

    @Before
    fun start() {
        val nodes = mockNet.createTwoNodes()
        notaryNode = nodes.first
        bankOfCordaNode = nodes.second
        notary = notaryNode.info.notaryIdentity
        bankOfCorda = bankOfCordaNode.info.legalIdentity

        notaryNode.services.identityService.registerIdentity(bankOfCordaNode.info.legalIdentityAndCert)
        bankOfCordaNode.services.identityService.registerIdentity(notaryNode.info.legalIdentityAndCert)
        val future = bankOfCordaNode.services.startFlow(CashIssueFlow(initialBalance, ref,
                bankOfCorda,
                notary)).resultFuture
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `pay some cash`() {
        val payTo = notaryNode.info.legalIdentity
        val expectedPayment = 500.DOLLARS
        val expectedChange = 1500.DOLLARS

        bankOfCordaNode.database.transaction {
            // Register for vault updates
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val (_, vaultUpdatesBoc) = bankOfCordaNode.services.vaultQueryService.trackBy<Cash.State>(criteria)
            val (_, vaultUpdatesBankClient) = notaryNode.services.vaultQueryService.trackBy<Cash.State>(criteria)

            val future = bankOfCordaNode.services.startFlow(CashPaymentFlow(expectedPayment,
                    payTo)).resultFuture
            mockNet.runNetwork()
            future.getOrThrow()

            // Check Bank of Corda vault updates - we take in some issued cash and split it into $500 to the notary
            // and $1,500 back to us, so we expect to consume one state, produce one state for our own vault
            vaultUpdatesBoc.expectEvents {
                expect { update ->
                    require(update.consumed.size == 1) { "Expected 1 consumed states, actual: $update" }
                    require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                    val changeState = update.produced.single().state.data as Cash.State
                    assertEquals(expectedChange.`issued by`(bankOfCorda.ref(ref)), changeState.amount)
                }
            }

            // Check notary node vault updates
            vaultUpdatesBankClient.expectEvents {
                expect { update ->
                    require(update.consumed.isEmpty()) { update.consumed.size }
                    require(update.produced.size == 1) { update.produced.size }
                    val paymentState = update.produced.single().state.data as Cash.State
                    assertEquals(expectedPayment.`issued by`(bankOfCorda.ref(ref)), paymentState.amount)
                }
            }
        }
    }

    @Test
    fun `pay more than we have`() {
        val payTo = notaryNode.info.legalIdentity
        val expected = 4000.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashPaymentFlow(expected,
                payTo)).resultFuture
        mockNet.runNetwork()
        assertFailsWith<CashException> {
            future.getOrThrow()
        }
    }

    @Test
    fun `pay zero cash`() {
        val payTo = notaryNode.info.legalIdentity
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashPaymentFlow(expected,
                payTo)).resultFuture
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            future.getOrThrow()
        }
    }
}
