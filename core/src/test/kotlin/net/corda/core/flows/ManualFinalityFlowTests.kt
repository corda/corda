package net.corda.core.flows

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.GBP
import net.corda.finance.contracts.asset.Cash
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualFinalityFlowTests {
    lateinit var mockNet: MockNetwork
    lateinit var nodeA: StartedNode<MockNetwork.MockNode>
    lateinit var nodeB: StartedNode<MockNetwork.MockNode>
    lateinit var nodeC: StartedNode<MockNetwork.MockNode>
    lateinit var notary: Party
    val services = MockServices()

    @Before
    fun setup() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes(3)
        nodeA = nodes.partyNodes[0]
        nodeB = nodes.partyNodes[1]
        nodeC = nodes.partyNodes[2]
        mockNet.runNetwork()
        nodeA.internals.ensureRegistered()
        notary = nodeA.services.networkMapCache.notaryIdentities.first().party
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `finalise a simple transaction`() {
        val amount = Amount(1000, Issued(nodeA.info.chooseIdentity().ref(0), GBP))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, nodeB.info.chooseIdentity(), notary)
        val stx = nodeA.services.signInitialTransaction(builder)
        val flow = nodeA.services.startFlow(ManualFinalityFlow(stx, setOf(nodeC.info.chooseIdentity())))
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