package com.r3.corda.enterprise.perftestcordapp.contracts.flows

import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import com.r3.corda.enterprise.perftestcordapp.DOLLARS
import com.r3.corda.enterprise.perftestcordapp.`issued by`
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import net.corda.node.internal.StartedNode
import com.r3.corda.enterprise.perftestcordapp.flows.CashIssueFlow
import net.corda.testing.chooseIdentity
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

class CashIssueFlowTests {
    private lateinit var mockNet : MockNetwork
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

        mockNet.runNetwork()
        notary = bankOfCordaNode.services.getDefaultNotary()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `issue some cash`() {
        val expected = 500.DOLLARS
        val ref = OpaqueBytes.of(0x01)
        val future = bankOfCordaNode.services.startFlow(CashIssueFlow(expected, ref, notary)).resultFuture
        mockNet.runNetwork()
        val issueTx = future.getOrThrow().stx
        val output = issueTx.tx.outputsOfType<Cash.State>().single()
        assertEquals(expected.`issued by`(bankOfCorda.ref(ref)), output.amount)
    }

    @Test
    fun `issue zero cash`() {
        val expected = 0.DOLLARS
        val ref = OpaqueBytes.of(0x01)
        val future = bankOfCordaNode.services.startFlow(CashIssueFlow(expected, ref, notary)).resultFuture
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            future.getOrThrow()
        }
    }
}
