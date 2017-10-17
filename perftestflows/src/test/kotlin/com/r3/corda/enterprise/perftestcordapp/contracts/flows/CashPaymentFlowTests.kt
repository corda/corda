package com.r3.corda.enterprise.perftestcordapp.contracts.flows

import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.trackBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import com.r3.corda.enterprise.perftestcordapp.DOLLARS
import com.r3.corda.enterprise.perftestcordapp.`issued by`
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.PtCash
import com.r3.corda.enterprise.perftestcordapp.flows.PtCashException
import com.r3.corda.enterprise.perftestcordapp.flows.PtCashIssueFlow
import com.r3.corda.enterprise.perftestcordapp.flows.PtCashPaymentFlow
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.getDefaultNotary
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.setCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CashPaymentFlowTests {
    private lateinit var mockNet : MockNetwork
    private val initialBalance = 2000.DOLLARS
    private val ref = OpaqueBytes.of(0x01)
    private lateinit var bankOfCordaNode: StartedNode<MockNode>
    private lateinit var bankOfCorda: Party
    private lateinit var notaryNode: StartedNode<MockNode>
    private lateinit var notary: Party

    @Before
    fun start() {
        setCordappPackages("com.r3.corda.enterprise.perftestcordapp.contracts.asset")
        mockNet = MockNetwork(servicePeerAllocationStrategy = RoundRobin())
        val nodes = mockNet.createSomeNodes(1)
        notaryNode = nodes.notaryNode
        bankOfCordaNode = nodes.partyNodes[0]
        bankOfCorda = bankOfCordaNode.info.chooseIdentity()
        notary = notaryNode.services.getDefaultNotary()
        val future = bankOfCordaNode.services.startFlow(PtCashIssueFlow(initialBalance, ref, notary)).resultFuture
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `pay some cash`() {
        val payTo = notaryNode.info.chooseIdentity()
        val expectedPayment = 500.DOLLARS
        val expectedChange = 1500.DOLLARS

        bankOfCordaNode.database.transaction {
            // Register for vault updates
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val (_, vaultUpdatesBoc) = bankOfCordaNode.services.vaultQueryService.trackBy<PtCash.State>(criteria)
            val (_, vaultUpdatesBankClient) = notaryNode.services.vaultQueryService.trackBy<PtCash.State>(criteria)

            val future = bankOfCordaNode.services.startFlow(PtCashPaymentFlow(expectedPayment,
                    payTo)).resultFuture
            mockNet.runNetwork()
            future.getOrThrow()

            // Check Bank of Corda vault updates - we take in some issued cash and split it into $500 to the notary
            // and $1,500 back to us, so we expect to consume one state, produce one state for our own vault
            vaultUpdatesBoc.expectEvents {
                expect { update ->
                    require(update.consumed.size == 1) { "Expected 1 consumed states, actual: $update" }
                    require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                    val changeState = update.produced.single().state.data
                    assertEquals(expectedChange.`issued by`(bankOfCorda.ref(ref)), changeState.amount)
                }
            }

            // Check notary node vault updates
            vaultUpdatesBankClient.expectEvents {
                expect { (consumed, produced) ->
                    require(consumed.isEmpty()) { consumed.size }
                    require(produced.size == 1) { produced.size }
                    val paymentState = produced.single().state.data
                    assertEquals(expectedPayment.`issued by`(bankOfCorda.ref(ref)), paymentState.amount)
                }
            }
        }
    }

    @Test
    fun `pay more than we have`() {
        val payTo = notaryNode.info.chooseIdentity()
        val expected = 4000.DOLLARS
        val future = bankOfCordaNode.services.startFlow(PtCashPaymentFlow(expected,
                payTo)).resultFuture
        mockNet.runNetwork()
        assertFailsWith<PtCashException> {
            future.getOrThrow()
        }
    }

    @Test
    fun `pay zero cash`() {
        val payTo = notaryNode.info.chooseIdentity()
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.services.startFlow(PtCashPaymentFlow(expected,
                payTo)).resultFuture
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            future.getOrThrow()
        }
    }
}
