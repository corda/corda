package net.corda.node.modes.draining

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.packageName
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.*
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowsDrainingModeContentionTest {

    companion object {
        private val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party

        // not to be captured by closures that won't be serialisable
        private lateinit var alice: StartedNode<InternalMockNetwork.MockNode>
        private lateinit var bob: StartedNode<InternalMockNetwork.MockNode>
    }

    private val mockNet = InternalMockNetwork(listOf(DummyContract::class.packageName))

    @Before
    fun before() {
        alice = mockNet.createPartyNode(ALICE_NAME)
        bob = mockNet.createPartyNode(BOB_NAME)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `draining mode does not deadlock with acks between 2 nodes`() {

        val message = "Ground control to Major Tom"

        val initiating = initiatingFlow<Any> @Suspendable {

            val session = initiateFlow(bob.info.singleIdentity())
            val transaction = TransactionBuilder(notary = DUMMY_NOTARY)
            transaction.addOutputState(StringTypeDummyState("hey"), DummyContract.PROGRAM_ID).addCommand(dummyCommand(serviceHub.myInfo.singleIdentity().owningKey))
            val signedTx = serviceHub.signInitialTransaction(transaction)
            subFlow(SendTransactionFlow(session, signedTx))
            waitForLedgerCommit(signedTx.id)
            message
        }

        bob.registerInitiatedFlow(initiating::class) { session ->

            object : FlowLogic<Unit>() {

                @Suspendable
                override fun call() {

                    val tx = subFlow(ReceiveTransactionFlow(session))
                    logger.info("Got transaction from counterParty.")
                    val signedTx = serviceHub.addSignature(tx)
                    assertThat(signedTx.id).isEqualTo(tx.id)
                    alice.services.nodeProperties.flowsDrainingMode.setEnabled(true)
                    subFlow(FinalityFlow(signedTx))
                }
            } as FlowLogic<Unit>
        }

        val flow = alice.services.startFlow(initiating)
        mockNet.runNetwork()
        val receivedAnswer = flow.resultFuture.getOrThrow()
        assertThat(receivedAnswer).isEqualTo(message)
    }
}

private class StringTypeDummyState(val data: String) : ContractState {

    override val participants: List<AbstractParty> = emptyList()
}