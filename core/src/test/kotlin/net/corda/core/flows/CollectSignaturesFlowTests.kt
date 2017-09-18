package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.StartedNode
import net.corda.testing.MINI_CORP_KEY
import net.corda.testing.chooseIdentity
import net.corda.testing.chooseIdentityAndCert
import net.corda.testing.contracts.DUMMY_PROGRAM_ID
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

class CollectSignaturesFlowTests {
    lateinit var mockNet: MockNetwork
    lateinit var a: StartedNode<MockNetwork.MockNode>
    lateinit var b: StartedNode<MockNetwork.MockNode>
    lateinit var c: StartedNode<MockNetwork.MockNode>
    lateinit var notary: Party
    val services = MockServices()

    @Before
    fun setup() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes(3)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        c = nodes.partyNodes[2]
        notary = nodes.notaryNode.info.notaryIdentity
        mockNet.runNetwork()
        a.internals.ensureRegistered()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    private fun registerFlowOnAllNodes(flowClass: KClass<out FlowLogic<*>>) {
        listOf(a, b, c).forEach {
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

                subFlow(flow)
                return subFlow(ReceiveTransactionFlow(session))
            }
        }

        @InitiatedBy(TestFlow.Initiator::class)
        class Responder(private val initiatingSession: FlowSession) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val state = initiatingSession.receive<DummyContract.MultiOwnerState>().unwrap { it }
                val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity

                val myInputKeys = state.participants.map { it.owningKey }
                val command = Command(DummyContract.Commands.Create(), myInputKeys)
                val builder = TransactionBuilder(notary).withItems(StateAndContract(state, DUMMY_PROGRAM_ID), command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val signature = subFlow(CollectSignatureFlow(ptx, initiatingSession, initiatingSession.counterparty.owningKey))
                val stx = ptx + signature
                return subFlow(FinalityFlow(stx, initiatingSession))
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
                val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity
                val myInputKeys = state.participants.map { it.owningKey }
                val command = Command(DummyContract.Commands.Create(), myInputKeys)
                val builder = TransactionBuilder(notary).withItems(StateAndContract(state, DUMMY_PROGRAM_ID), command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val sessions = state.owners
                        .mapNotNull { serviceHub.identityService.partyFromAnonymous(it) }
                        .toSet()
                        .mapNotNull { if (serviceHub.myInfo.isLegalIdentity(it)) null else initiateFlow(it) }
                val stx = subFlow(CollectSignaturesFlow(ptx, myInputKeys))
                return subFlow(FinalityFlow(stx, sessions))
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

                subFlow(signFlow)
                subFlow(ReceiveTransactionFlow(otherSideSession))
            }
        }
    }

    @Test
    fun `successfully collects two signatures`() {
        val bConfidentialIdentity = b.database.transaction {
            b.services.keyManagementService.freshKeyAndCert(b.info.chooseIdentityAndCert(), false)
        }
        a.database.transaction {
            // Normally this is handled by TransactionKeyFlow, but here we have to manually let A know about the identity
            a.services.identityService.verifyAndRegisterIdentity(bConfidentialIdentity)
        }
        registerFlowOnAllNodes(TestFlowTwo.Responder::class)
        val magicNumber = 1337
        val parties = listOf(a.info.chooseIdentity(), bConfidentialIdentity.party, c.info.chooseIdentity())
        val state = DummyContract.MultiOwnerState(magicNumber, parties)
        val flow = a.services.startFlow(TestFlowTwo.Initiator(state))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifyRequiredSignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `no need to collect any signatures`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, a.info.chooseIdentity().ref(1))
        val ptx = a.services.signInitialTransaction(onePartyDummyContract)
        val flow = a.services.startFlow(CollectSignaturesFlow(ptx))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifyRequiredSignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `fails when not signed by initiator`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, a.info.chooseIdentity().ref(1))
        val miniCorpServices = MockServices(MINI_CORP_KEY)
        val ptx = miniCorpServices.signInitialTransaction(onePartyDummyContract)
        val flow = a.services.startFlow(CollectSignaturesFlow(ptx))
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException>("The Initiator of CollectSignaturesFlow must have signed the transaction.") {
            flow.resultFuture.getOrThrow()
        }
    }

    @Test
    fun `passes with multiple initial signatures`() {
        val twoPartyDummyContract = DummyContract.generateInitial(1337, notary,
                a.info.chooseIdentity().ref(1),
                b.info.chooseIdentity().ref(2),
                b.info.chooseIdentity().ref(3))
        val signedByA = a.services.signInitialTransaction(twoPartyDummyContract)
        val signedByBoth = b.services.addSignature(signedByA)
        val flow = a.services.startFlow(CollectSignaturesFlow(signedByBoth))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        println(result.tx)
        println(result.sigs)
    }
}
