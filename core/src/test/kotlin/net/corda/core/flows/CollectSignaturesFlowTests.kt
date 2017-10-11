package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.StartedNode
import net.corda.testing.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

class CollectSignaturesFlowTests {
    companion object {
        private val cordappPackages = listOf("net.corda.testing.contracts")
    }

    lateinit var mockNet: MockNetwork
    lateinit var aliceNode: StartedNode<MockNetwork.MockNode>
    lateinit var bobNode: StartedNode<MockNetwork.MockNode>
    lateinit var charlieNode: StartedNode<MockNetwork.MockNode>
    lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(cordappPackages = cordappPackages)
        val notaryNode = mockNet.createNotaryNode()
        aliceNode = mockNet.createPartyNode(ALICE.name)
        bobNode = mockNet.createPartyNode(BOB.name)
        charlieNode = mockNet.createPartyNode(CHARLIE.name)
        mockNet.runNetwork()
        notary = notaryNode.services.getDefaultNotary()
        aliceNode.internals.ensureRegistered()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    private fun registerFlowOnAllNodes(flowClass: KClass<out FlowLogic<*>>) {
        listOf(aliceNode, bobNode, charlieNode).forEach {
            it.internals.registerInitiatedFlow(flowClass.java)
        }
    }

    // With this flow, the initiators sends an "offer" to the responder, who then initiates the collect signatures flow.
    // This flow is a more simplified version of the "TwoPartyTrade" flow and is a useful example of how both the
    // "collectSignaturesFlow" and "SignTransactionFlow" can be used in practise.
    object TestFlow {
        @InitiatingFlow
        class Initiator(private val state: DummyContract.MultiOwnerState, private val otherParty: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val session = initiateFlow(otherParty)
                session.send(state)

                val flow = object : SignTransactionFlow(session) {
                    @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val tx = stx.tx
                        val ltx = tx.toLedgerTransaction(serviceHub)
                        "There should only be one output state" using (tx.outputs.size == 1)
                        "There should only be one output state" using (tx.inputs.isEmpty())
                        val magicNumberState = ltx.outputsOfType<DummyContract.MultiOwnerState>().single()
                        "Must be 1337 or greater" using (magicNumberState.magicNumber >= 1337)
                    }
                }

                val stx = subFlow(flow)
                return waitForLedgerCommit(stx.id)
            }
        }

        @InitiatedBy(TestFlow.Initiator::class)
        class Responder(private val initiatingSession: FlowSession) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val state = initiatingSession.receive<DummyContract.MultiOwnerState>().unwrap { it }
                val notary = serviceHub.getDefaultNotary()

                val myInputKeys = state.participants.map { it.owningKey }
                val command = Command(DummyContract.Commands.Create(), myInputKeys)
                val builder = TransactionBuilder(notary).withItems(StateAndContract(state, DummyContract.PROGRAM_ID), command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val signature = subFlow(CollectSignatureFlow(ptx, initiatingSession, initiatingSession.counterparty.owningKey))
                val stx = ptx + signature
                return subFlow(FinalityFlow(stx))
            }
        }
    }

    // With this flow, the initiator starts the "CollectTransactionFlow". It is then the responders responsibility to
    // override "checkTransaction" and add whatever logic their require to verify the SignedTransaction they are
    // receiving off the wire.
    object TestFlowTwo {
        @InitiatingFlow
        class Initiator(private val state: DummyContract.MultiOwnerState) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val notary = serviceHub.getDefaultNotary()
                val myInputKeys = state.participants.map { it.owningKey }
                val command = Command(DummyContract.Commands.Create(), myInputKeys)
                val builder = TransactionBuilder(notary).withItems(StateAndContract(state, DummyContract.PROGRAM_ID), command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val sessions = excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, state.owners)).map { initiateFlow(it.key) }
                val stx = subFlow(CollectSignaturesFlow(ptx, sessions, myInputKeys))
                return subFlow(FinalityFlow(stx))
            }
        }

        @InitiatedBy(TestFlowTwo.Initiator::class)
        class Responder(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable override fun call() {
                val signFlow = object : SignTransactionFlow(otherSideSession) {
                    @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val tx = stx.tx
                        val ltx = tx.toLedgerTransaction(serviceHub)
                        "There should only be one output state" using (tx.outputs.size == 1)
                        "There should only be one output state" using (tx.inputs.isEmpty())
                        val magicNumberState = ltx.outputsOfType<DummyContract.MultiOwnerState>().single()
                        "Must be 1337 or greater" using (magicNumberState.magicNumber >= 1337)
                    }
                }

                val stx = subFlow(signFlow)
                waitForLedgerCommit(stx.id)
            }
        }
    }

    @Test
    fun `successfully collects two signatures`() {
        val bConfidentialIdentity = bobNode.database.transaction {
            bobNode.services.keyManagementService.freshKeyAndCert(bobNode.info.chooseIdentityAndCert(), false)
        }
        aliceNode.database.transaction {
            // Normally this is handled by TransactionKeyFlow, but here we have to manually let A know about the identity
            aliceNode.services.identityService.verifyAndRegisterIdentity(bConfidentialIdentity)
        }
        registerFlowOnAllNodes(TestFlowTwo.Responder::class)
        val magicNumber = 1337
        val parties = listOf(aliceNode.info.chooseIdentity(), bConfidentialIdentity.party, charlieNode.info.chooseIdentity())
        val state = DummyContract.MultiOwnerState(magicNumber, parties)
        val flow = aliceNode.services.startFlow(TestFlowTwo.Initiator(state))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifyRequiredSignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `no need to collect any signatures`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, aliceNode.info.chooseIdentity().ref(1))
        val ptx = aliceNode.services.signInitialTransaction(onePartyDummyContract)
        val flow = aliceNode.services.startFlow(CollectSignaturesFlow(ptx, emptySet()))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifyRequiredSignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `fails when not signed by initiator`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, aliceNode.info.chooseIdentity().ref(1))
        val miniCorpServices = MockServices(cordappPackages, MINI_CORP_KEY)
        val ptx = miniCorpServices.signInitialTransaction(onePartyDummyContract)
        val flow = aliceNode.services.startFlow(CollectSignaturesFlow(ptx, emptySet()))
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException>("The Initiator of CollectSignaturesFlow must have signed the transaction.") {
            flow.resultFuture.getOrThrow()
        }
    }

    @Test
    fun `passes with multiple initial signatures`() {
        val twoPartyDummyContract = DummyContract.generateInitial(1337, notary,
                aliceNode.info.chooseIdentity().ref(1),
                bobNode.info.chooseIdentity().ref(2),
                bobNode.info.chooseIdentity().ref(3))
        val signedByA = aliceNode.services.signInitialTransaction(twoPartyDummyContract)
        val signedByBoth = bobNode.services.addSignature(signedByA)
        val flow = aliceNode.services.startFlow(CollectSignaturesFlow(signedByBoth, emptySet()))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        println(result.tx)
        println(result.sigs)
    }
}
