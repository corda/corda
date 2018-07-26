package net.corda.groups.flows

import net.corda.core.toFuture
import net.corda.core.utilities.getOrThrow
import net.corda.groups.contracts.Group
import net.corda.testing.node.internal.TestStartedNode
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class DataDistributionGroupTests : MockNetworkTest(numberOfNodes = 5) {

    lateinit var A: TestStartedNode
    lateinit var B: TestStartedNode
    lateinit var C: TestStartedNode
    lateinit var D: TestStartedNode
    lateinit var E: TestStartedNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        C = nodes[2]
        D = nodes[3]
        E = nodes[4]
    }

    @Test
    fun `Can create a new group`() {
        val future = A.services.vaultService.updates.filter { it.containsType<Group.State>() }.toFuture()
        val flowResult = A.createGroup("Test group").getOrThrow()
        val groupFromVault = future.get().produced.single().state.data
        val groupFromStx = flowResult.tx.outputsOfType<Group.State>().single()
        assertEquals(groupFromStx, groupFromVault)
    }

    @Test
    fun `can invite nodes to group`() {
        val groupKey = A.createGroup("Test group").getOrThrow().getSingleOutput<Group.State>().key()
        val aToB = A.inviteToGroup(groupKey, B).getOrThrow()
        val bToC = B.inviteToGroup(groupKey, C).getOrThrow()
        val bToD = B.inviteToGroup(groupKey, D).getOrThrow()
        val cToE = C.inviteToGroup(groupKey, E).getOrThrow()
        // Check everyone has the
        assertEquals(
                setOf(
                        aToB.getSingleOutput<Group.State>().key(),
                        bToC.getSingleOutput<Group.State>().key(),
                        bToD.getSingleOutput<Group.State>().key(),
                        cToE.getSingleOutput<Group.State>().key()
                ).size, 1
        )
    }

    @Test
    fun `can add transaction to group and everyone gets it`() {
        val groupKey = A.createGroup("Test group").getOrThrow().getSingleOutput<Group.State>().key()
        A.inviteToGroup(groupKey, B).getOrThrow()
        B.inviteToGroup(groupKey, C).getOrThrow()
        B.inviteToGroup(groupKey, D).getOrThrow()
        C.inviteToGroup(groupKey, E).getOrThrow()
        val stx = A.createDummyTransaction().getOrThrow()
        val bTx = B.watchForTransaction(stx.id).toCompletableFuture()
        val cTx = C.watchForTransaction(stx.id).toCompletableFuture()
        val dTx = D.watchForTransaction(stx.id).toCompletableFuture()
        val eTx = E.watchForTransaction(stx.id).toCompletableFuture()
        A.addToGroup(groupKey, stx)
        // If this future completes then B, C, D and E have seen the
        // transaction submitted to the data distribution group.
        CompletableFuture.allOf(bTx, cTx, dTx, eTx).getOrThrow()
    }

}