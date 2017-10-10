package net.corda.docs

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class WorkflowTransactionBuildTutorialTest {
    lateinit var mockNet: MockNetwork
    lateinit var nodeA: StartedNode<MockNetwork.MockNode>
    lateinit var nodeB: StartedNode<MockNetwork.MockNode>

    // Helper method to locate the latest Vault version of a LinearState
    private inline fun <reified T : LinearState> ServiceHub.latest(ref: UniqueIdentifier): StateAndRef<T> {
        val linearHeads = vaultService.queryBy<T>(QueryCriteria.LinearStateQueryCriteria(uuid = listOf(ref.id)))
        return linearHeads.states.single()
    }

    @Before
    fun setup() {
        setCordappPackages("net.corda.docs")
        mockNet = MockNetwork(threadPerNode = true)
        mockNet.createNotaryNode(legalName = DUMMY_NOTARY.name)
        nodeA = mockNet.createPartyNode()
        nodeB = mockNet.createPartyNode()
        nodeA.internals.registerInitiatedFlow(RecordCompletionFlow::class.java)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `Run workflow to completion`() {
        // Setup a vault subscriber to wait for successful upload of the proposal to NodeB
        val nodeBVaultUpdate = nodeB.services.vaultService.updates.toFuture()
        // Kick of the proposal flow
        val flow1 = nodeA.services.startFlow(SubmitTradeApprovalFlow("1234", nodeB.info.chooseIdentity()))
        // Wait for the flow to finish
        val proposalRef = flow1.resultFuture.getOrThrow()
        val proposalLinearId = proposalRef.state.data.linearId
        // Wait for NodeB to include it's copy in the vault
        nodeBVaultUpdate.get()
        // Fetch the latest copy of the state from both nodes
        val latestFromA = nodeA.database.transaction {
            nodeA.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        val latestFromB = nodeB.database.transaction {
            nodeB.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        // Confirm the state as as expected
        assertEquals(WorkflowState.NEW, proposalRef.state.data.state)
        assertEquals("1234", proposalRef.state.data.tradeId)
        assertEquals(nodeA.info.chooseIdentity(), proposalRef.state.data.source)
        assertEquals(nodeB.info.chooseIdentity(), proposalRef.state.data.counterparty)
        assertEquals(proposalRef, latestFromA)
        assertEquals(proposalRef, latestFromB)
        // Setup a vault subscriber to pause until the final update is in NodeA and NodeB
        val nodeAVaultUpdate = nodeA.services.vaultService.updates.toFuture()
        val secondNodeBVaultUpdate = nodeB.services.vaultService.updates.toFuture()
        // Run the manual completion flow from NodeB
        val flow2 = nodeB.services.startFlow(SubmitCompletionFlow(latestFromB.ref, WorkflowState.APPROVED))
        // wait for the flow to end
        val completedRef = flow2.resultFuture.getOrThrow()
        // wait for the vault updates to stabilise
        nodeAVaultUpdate.get()
        secondNodeBVaultUpdate.get()
        // Fetch the latest copies from the vault
        val finalFromA = nodeA.database.transaction {
            nodeA.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        val finalFromB = nodeB.database.transaction {
            nodeB.services.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        // Confirm the state is as expected
        assertEquals(WorkflowState.APPROVED, completedRef.state.data.state)
        assertEquals("1234", completedRef.state.data.tradeId)
        assertEquals(nodeA.info.chooseIdentity(), completedRef.state.data.source)
        assertEquals(nodeB.info.chooseIdentity(), completedRef.state.data.counterparty)
        assertEquals(completedRef, finalFromA)
        assertEquals(completedRef, finalFromB)
    }
}
