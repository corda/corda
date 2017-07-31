package net.corda.core.flows

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.GBP
import net.corda.core.contracts.Issued
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.getOrThrow
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualFinalityFlowTests {
    lateinit var mockNet: MockNetwork
    lateinit var nodeA: MockNetwork.MockNode
    lateinit var nodeB: MockNetwork.MockNode
    lateinit var nodeC: MockNetwork.MockNode
    lateinit var notary: Party
    val services = MockServices()

    @Before
    fun setup() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes(3)
        nodeA = nodes.partyNodes[0]
        nodeB = nodes.partyNodes[1]
        nodeC = nodes.partyNodes[2]
        notary = nodes.notaryNode.info.notaryIdentity
        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `finalise a simple transaction`() {
        val amount = Amount(1000, Issued(nodeA.info.legalIdentity.ref(0), GBP))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, nodeB.info.legalIdentity, notary)
        val stx = nodeA.services.signInitialTransaction(builder)
        val flow = nodeA.services.startFlow(ManualFinalityFlow(stx, setOf(nodeC.info.legalIdentity)))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        val notarisedTx = result.single()
        notarisedTx.verifyRequiredSignatures()
        // We override the participants, so node C will get a copy despite not being involved, and B won't
        val transactionSeenByB = nodeB.services.database.transaction {
            nodeB.services.validatedTransactions.getTransaction(notarisedTx.id)
        }
        assertNull(transactionSeenByB)
        val transactionSeenByC = nodeC.services.database.transaction {
            nodeC.services.validatedTransactions.getTransaction(notarisedTx.id)
        }
        assertEquals(notarisedTx, transactionSeenByC)
    }
}