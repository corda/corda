package net.corda.finance.flows

import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.node.internal.StartedNode
import net.corda.testing.BOC_NAME
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CashExitFlowTests {
    private lateinit var mockNet: MockNetwork
    private val initialBalance = 2000.DOLLARS
    private val ref = OpaqueBytes.of(0x01)
    private lateinit var bankOfCordaNode: StartedNode<MockNode>
    private lateinit var bankOfCorda: Party
    private lateinit var notary: Party

    @Before
    fun start() {
        mockNet = MockNetwork(servicePeerAllocationStrategy = RoundRobin(),
                cordappPackages = setOf("net.corda.finance.contracts.asset"))
        bankOfCordaNode = mockNet.createPartyNode(BOC_NAME)
        bankOfCorda = bankOfCordaNode.info.identityFromX500Name(BOC_NAME)
        notary = mockNet.defaultNotaryIdentity
        val future = bankOfCordaNode.services.startFlow(CashIssueFlow(initialBalance, ref, notary)).resultFuture
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `exit some cash`() {
        val exitAmount = 500.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashExitFlow(exitAmount, ref)).resultFuture
        mockNet.runNetwork()
        val exitTx = future.getOrThrow().stx.tx
        val expected = (initialBalance - exitAmount).`issued by`(bankOfCorda.ref(ref))
        assertEquals(1, exitTx.inputs.size)
        assertEquals(1, exitTx.outputs.size)
        val output = exitTx.outputsOfType<Cash.State>().single()
        assertEquals(expected, output.amount)
    }

    @Test
    fun `exit zero cash`() {
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashExitFlow(expected, ref)).resultFuture
        mockNet.runNetwork()
        assertFailsWith<CashException> {
            future.getOrThrow()
        }
    }
}
