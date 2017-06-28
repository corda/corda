package net.corda.flows

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.`issued by`
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.serialization.OpaqueBytes
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CashIssueFlowTests {
    private val mockNet = MockNetwork(servicePeerAllocationStrategy = RoundRobin())
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

        mockNet.runNetwork()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `issue some cash`() {
        val expected = 500.DOLLARS
        val ref = OpaqueBytes.of(0x01)
        val future = bankOfCordaNode.services.startFlow(CashIssueFlow(expected, ref,
                bankOfCorda,
                notary)).resultFuture
        mockNet.runNetwork()
        val issueTx = future.getOrThrow().stx
        val output = issueTx.tx.outputs.single().data as Cash.State
        assertEquals(expected.`issued by`(bankOfCorda.ref(ref)), output.amount)
    }

    @Test
    fun `issue zero cash`() {
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashIssueFlow(expected, OpaqueBytes.of(0x01),
                bankOfCorda,
                notary)).resultFuture
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            future.getOrThrow()
        }
    }
}
