package net.corda.coretests.contracts

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test

class ContractHierarchyTest {
    private lateinit var mockNet: InternalMockNetwork

    @Before
    fun before() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = InternalMockNetwork(networkSendManuallyPumped = false, threadPerNode = true, cordappsForAllNodes = listOf(enclosedCordapp()))
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `hierarchical contracts work with mock network`() {
        // Set up values we'll need
        val aliceNode = mockNet.createPartyNode(ALICE_NAME)
        val bobNode = mockNet.createPartyNode(BOB_NAME)
        val bob: Party = bobNode.info.singleIdentity()
        val notary = mockNet.defaultNotaryIdentity
        bobNode.registerInitiatedFlow(AcceptTransaction::class.java)

        // With a state annotated with @BelongsToContract.
        aliceNode.services.startFlow(PrepareTransaction(bob, notary, StubbedState())).resultFuture.getOrThrow()

        // With a state enclosed by a contract class.
        aliceNode.services.startFlow(PrepareTransaction(bob, notary, IndirectContract.State())).resultFuture.getOrThrow()
    }

    interface StubbedContractParent : Contract

    open class IndirectContractParent : StubbedContractParent {
        interface Commands : CommandData
        open class Create : IndirectContract.Commands

        override fun verify(tx: LedgerTransaction) {
            throw RuntimeException("Boom!")
        }
    }

    abstract class StubbedStateParent : ContractState {
        override val participants: List<AbstractParty>
            get() = emptyList()
    }

    @BelongsToContract(IndirectContract::class)
    class StubbedState : StubbedStateParent()

    class IndirectContract : IndirectContractParent() {

        class State : StubbedStateParent()

        interface Commands : IndirectContractParent.Commands
        class Create : IndirectContractParent.Commands

        override fun verify(tx: LedgerTransaction) {
            //do nothing
        }
    }

    /**
     * Very lightweight wrapping flow to trigger the counterparty flow that receives the identities.
     */
    @InitiatingFlow
    class PrepareTransaction(private val otherSide: Party, private val notary: Party, private val state: StubbedStateParent) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(otherSide)
            val txState = TransactionState(data = state, notary = notary)
            val command = Command(IndirectContract.Create(), listOf(serviceHub.myInfo.singleIdentity().owningKey, otherSide.owningKey))
            val txB = TransactionBuilder(notary = notary, outputs = mutableListOf(txState), commands = mutableListOf(command))
            val tx = serviceHub.signInitialTransaction(txB)
            session.send(tx)
        }
    }

    @InitiatedBy(PrepareTransaction::class)
    class AcceptTransaction(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            var tx = otherSideSession.receive<SignedTransaction>().unwrap { it }
            tx.verify(serviceHub, checkSufficientSignatures = false)
            tx = serviceHub.addSignature(tx)
            subFlow(FinalityFlow(tx, otherSideSession))
        }
    }
}