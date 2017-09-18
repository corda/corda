package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FinalityFlowTests {
    lateinit var mockNet: MockNetwork
    lateinit var nodeA: StartedNode<MockNetwork.MockNode>
    lateinit var nodeB: StartedNode<MockNetwork.MockNode>
    lateinit var notary: Party
    val services = MockServices()

    @Before
    fun setup() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes(2)
        nodeA = nodes.partyNodes[0]
        nodeB = nodes.partyNodes[1]
        notary = nodes.notaryNode.info.notaryIdentity
        mockNet.runNetwork()
        nodeA.internals.ensureRegistered()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `finalise a simple transaction`() {
        nodeB.registerInitiatedFlow(RecordTx::class.java)
        val amount = 1000.POUNDS.issuedBy(nodeA.info.chooseIdentity().ref(0))
        val bIdentity = nodeB.info.chooseIdentity()
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, bIdentity, notary)
        val stx = nodeA.services.signInitialTransaction(builder)
        val flow = nodeA.services.startFlow(Finaliser(stx, bIdentity))
        mockNet.runNetwork()
        val notarisedTx = flow.resultFuture.getOrThrow()
        notarisedTx.verifyRequiredSignatures()
        val transactionSeenByB = nodeB.services.database.transaction {
            nodeB.services.validatedTransactions.getTransaction(notarisedTx.id)
        }
        assertEquals(notarisedTx, transactionSeenByB)
    }

    @InitiatingFlow
    private class Finaliser(private val stx: SignedTransaction, private val otherParty: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction = subFlow(FinalityFlow(stx, initiateFlow(otherParty)))
    }

    @InitiatedBy(Finaliser::class)
    private class RecordTx(private val source: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(source))
        }
    }
}