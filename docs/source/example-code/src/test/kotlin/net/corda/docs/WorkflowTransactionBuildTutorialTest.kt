package net.corda.docs

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.api.StartedNodeServices
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class WorkflowTransactionBuildTutorialTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var aliceServices: StartedNodeServices
    private lateinit var bobServices: StartedNodeServices
    private lateinit var alice: Party
    private lateinit var bob: Party

    // Helper method to locate the latest Vault version of a LinearState
    private inline fun <reified T : LinearState> ServiceHub.latest(ref: UniqueIdentifier): StateAndRef<T> {
        val linearHeads = vaultService.queryBy<T>(QueryCriteria.LinearStateQueryCriteria(uuid = listOf(ref.id)))
        return linearHeads.states.single()
    }

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true, cordappPackages = setOf("net.corda.docs"))
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        aliceNode.registerInitiatedFlow(RecordCompletionFlow::class.java)
        aliceServices = aliceNode.services
        bobServices = bobNode.services
        alice = aliceNode.services.myInfo.identityFromX500Name(ALICE_NAME)
        bob = bobNode.services.myInfo.identityFromX500Name(BOB_NAME)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `Run workflow to completion`() {
        // Setup a vault subscriber to wait for successful upload of the proposal to NodeB
        val nodeBVaultUpdate = bobServices.vaultService.updates.toFuture()
        // Kick of the proposal flow
        val flow1 = aliceServices.startFlow(SubmitTradeApprovalFlow("1234", bob))
        // Wait for the flow to finish
        val proposalRef = flow1.resultFuture.getOrThrow()
        val proposalLinearId = proposalRef.state.data.linearId
        // Wait for NodeB to include it's copy in the vault
        nodeBVaultUpdate.get()
        // Fetch the latest copy of the state from both nodes
        val latestFromA = aliceServices.database.transaction {
            aliceServices.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        val latestFromB = bobServices.database.transaction {
            bobServices.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        // Confirm the state as as expected
        assertEquals(WorkflowState.NEW, proposalRef.state.data.state)
        assertEquals("1234", proposalRef.state.data.tradeId)
        assertEquals(alice, proposalRef.state.data.source)
        assertEquals(bob, proposalRef.state.data.counterparty)
        assertEquals(proposalRef, latestFromA)
        assertEquals(proposalRef, latestFromB)
        // Setup a vault subscriber to pause until the final update is in NodeA and NodeB
        val nodeAVaultUpdate = aliceServices.vaultService.updates.toFuture()
        val secondNodeBVaultUpdate = bobServices.vaultService.updates.toFuture()
        // Run the manual completion flow from NodeB
        val flow2 = bobServices.startFlow(SubmitCompletionFlow(latestFromB.ref, WorkflowState.APPROVED))
        // wait for the flow to end
        val completedRef = flow2.resultFuture.getOrThrow()
        // wait for the vault updates to stabilise
        nodeAVaultUpdate.get()
        secondNodeBVaultUpdate.get()
        // Fetch the latest copies from the vault
        val finalFromA = aliceServices.database.transaction {
            aliceServices.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        val finalFromB = bobServices.database.transaction {
            bobServices.latest<TradeApprovalContract.State>(proposalLinearId)
        }
        // Confirm the state is as expected
        assertEquals(WorkflowState.APPROVED, completedRef.state.data.state)
        assertEquals("1234", completedRef.state.data.tradeId)
        assertEquals(alice, completedRef.state.data.source)
        assertEquals(bob, completedRef.state.data.counterparty)
        assertEquals(completedRef, finalFromA)
        assertEquals(completedRef, finalFromB)
    }
}
