package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.assertion.assert
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.flows.matchers.flow.willReturn
import net.corda.core.flows.matchers.flow.willThrow
import net.corda.core.flows.mixins.WithContracts
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.AfterClass
import org.junit.Test

class CollectSignaturesFlowTests : WithContracts {
    companion object {
        private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        private val miniCorpServices = MockServices(listOf("net.corda.testing.contracts"), miniCorp, rigorousMock())
        private val classMockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages("net.corda.testing.contracts", "net.corda.core.flows"))

        private const val MAGIC_NUMBER = 1337

        @JvmStatic
        @AfterClass
        fun tearDown() = classMockNet.stopNodes()
    }

    override val mockNet = classMockNet

    private val aliceNode = makeNode(ALICE_NAME)
    private val bobNode = makeNode(BOB_NAME)
    private val charlieNode = makeNode(CHARLIE_NAME)

    private val alice = aliceNode.info.singleIdentity()
    private val bob = bobNode.info.singleIdentity()
    private val charlie = charlieNode.info.singleIdentity()

    @Test
    fun `successfully collects three signatures`() {
        val bConfidentialIdentity = bobNode.createConfidentialIdentity(bob)
        aliceNode.verifyAndRegister(bConfidentialIdentity)

        assert.that(
            aliceNode.startTestFlow(alice, bConfidentialIdentity.party, charlie),
                willReturn(requiredSignatures(3))
        )
    }

    @Test
    fun `no need to collect any signatures`() {
        val ptx = aliceNode.signDummyContract(alice.ref(1))

        assert.that(
                aliceNode.collectSignatures(ptx),
                willReturn(requiredSignatures(1))
        )
    }

    @Test
    fun `fails when not signed by initiator`() {
        val ptx = miniCorpServices.signDummyContract(alice.ref(1))

        assert.that(
                aliceNode.collectSignatures(ptx),
                willThrow(errorMessage("The Initiator of CollectSignaturesFlow must have signed the transaction.")))
    }

    @Test
    fun `passes with multiple initial signatures`() {
        val signedByA = aliceNode.signDummyContract(
                alice.ref(1),
                MAGIC_NUMBER,
                bob.ref(2),
                bob.ref(3))
        val signedByBoth = bobNode.addSignatureTo(signedByA)

        assert.that(
                aliceNode.collectSignatures(signedByBoth),
                willReturn(requiredSignatures(2))
        )
    }

    //region Operators
    private fun StartedNode<*>.startTestFlow(vararg party: Party) =
            startFlowAndRunNetwork(
                TestFlow.Initiator(DummyContract.MultiOwnerState(
                    MAGIC_NUMBER,
                    listOf(*party)),
                    mockNet.defaultNotaryIdentity))

    //region Test Flow
    // With this flow, the initiator starts the "CollectTransactionFlow". It is then the responders responsibility to
    // override "checkTransaction" and add whatever logic their require to verify the SignedTransaction they are
    // receiving off the wire.
    object TestFlow {
        @InitiatingFlow
        class Initiator(private val state: DummyContract.MultiOwnerState, private val notary: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val myInputKeys = state.participants.map { it.owningKey }
                val command = Command(DummyContract.Commands.Create(), myInputKeys)
                val builder = TransactionBuilder(notary).withItems(StateAndContract(state, DummyContract.PROGRAM_ID), command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val sessions = excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, state.owners)).map { initiateFlow(it.key) }
                val stx = subFlow(CollectSignaturesFlow(ptx, sessions, myInputKeys))
                return subFlow(FinalityFlow(stx))
            }
        }

        @InitiatedBy(TestFlow.Initiator::class)
        class Responder(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                val signFlow = object : SignTransactionFlow(otherSideSession) {
                    @Suspendable
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val tx = stx.tx
                        val ltx = tx.toLedgerTransaction(serviceHub)
                        "There should only be one output state" using (tx.outputs.size == 1)
                        "There should only be one output state" using (tx.inputs.isEmpty())
                        val magicNumberState = ltx.outputsOfType<DummyContract.MultiOwnerState>().single()
                        "Must be $MAGIC_NUMBER or greater" using (magicNumberState.magicNumber >= MAGIC_NUMBER)
                    }
                }

                val stx = subFlow(signFlow)
                waitForLedgerCommit(stx.id)
            }
        }
    }
    //region
}
