package net.corda.docs

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.getOrThrow
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.DUMMY_NOTARY_KEY
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class WorkflowTransactionBuildTutorialTest {
    lateinit var mockNet: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var nodeA: MockNetwork.MockNode
    lateinit var nodeB: MockNetwork.MockNode

    // Helper method to locate the latest Vault version of a LinearState from a possibly out of date StateRef
    private inline fun <reified T : LinearState> ServiceHub.latest(ref: UniqueIdentifier): StateAndRef<T> {
        val linearHeads = vaultQueryService.queryBy<T>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(ref))
                                                  .and(QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)))
        return linearHeads.states.single()
    }

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true)
        val notaryService = ServiceInfo(ValidatingNotaryService.type)
        notaryNode = mockNet.createNode(
                legalName = DUMMY_NOTARY.name,
                overrideServices = mapOf(Pair(notaryService, DUMMY_NOTARY_KEY)),
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), notaryService))
        nodeA = mockNet.createPartyNode(notaryNode.network.myAddress)
        nodeB = mockNet.createPartyNode(notaryNode.network.myAddress)
        nodeA.registerInitiatedFlow(RecordCompletionFlow::class.java)
    }

    @After
    fun cleanUp() {
        println("Close DB")
        mockNet.stopNodes()
    }

    @Test
    fun `Run workflow to completion`() {
        // Setup a vault subscriber to wait for successful upload of the proposal to NodeB
        val nodeBVaultUpdate = nodeB.services.vaultService.updates.toFuture()
        // Kick of the proposal flow
        val flow1 = nodeA.services.startFlow(SubmitTradeApprovalFlow("1234", nodeB.info.legalIdentity))
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
        assertEquals(nodeA.info.legalIdentity, proposalRef.state.data.source)
        assertEquals(nodeB.info.legalIdentity, proposalRef.state.data.counterparty)
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
        assertEquals(nodeA.info.legalIdentity, completedRef.state.data.source)
        assertEquals(nodeB.info.legalIdentity, completedRef.state.data.counterparty)
        assertEquals(completedRef, finalFromA)
        assertEquals(completedRef, finalFromB)
    }
}