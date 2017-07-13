package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.requireThat
import net.corda.testing.contracts.DummyContract
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.SignTransactionFlow
import net.corda.testing.MINI_CORP_KEY
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

class CollectSignaturesFlowTests {
    lateinit var mockNet: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var c: MockNetwork.MockNode
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
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    private fun registerFlowOnAllNodes(flowClass: KClass<out FlowLogic<*>>) {
        listOf(a, b, c).forEach {
            it.registerInitiatedFlow(flowClass.java)
        }
    }

    // With this flow, the initiators sends an "offer" to the responder, who then initiates the collect signatures flow.
    // This flow is a more simplifed version of the "TwoPartyTrade" flow and is a useful example of how both the
    // "collectSignaturesFlow" and "SignTransactionFlow" can be used in practise.
    object TestFlow {
        @InitiatingFlow
        class Initiator(val state: DummyContract.MultiOwnerState, val otherParty: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                send(otherParty, state)

                val flow = object : SignTransactionFlow(otherParty) {
                    @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val tx = stx.tx
                        "There should only be one output state" using (tx.outputs.size == 1)
                        "There should only be one output state" using (tx.inputs.isEmpty())
                        val magicNumberState = tx.outputs.single().data as DummyContract.MultiOwnerState
                        "Must be 1337 or greater" using (magicNumberState.magicNumber >= 1337)
                    }
                }

                val stx = subFlow(flow)
                val ftx = waitForLedgerCommit(stx.id)

                return ftx
            }
        }

        @InitiatedBy(TestFlow.Initiator::class)
        class Responder(val otherParty: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val state = receive<DummyContract.MultiOwnerState>(otherParty).unwrap { it }
                val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity

                val command = Command(DummyContract.Commands.Create(), state.participants.map { it.owningKey })
                val builder = TransactionType.General.Builder(notary = notary).withItems(state, command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val stx = subFlow(CollectSignaturesFlow(ptx))
                val ftx = subFlow(FinalityFlow(stx)).single()

                return ftx
            }
        }
    }

    // With this flow, the initiator starts the "CollectTransactionFlow". It is then the responders responsibility to
    // override "checkTransaction" and add whatever logic their require to verify the SignedTransaction they are
    // receiving off the wire.
    object TestFlowTwo {
        @InitiatingFlow
        class Initiator(val state: DummyContract.MultiOwnerState) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity
                val command = Command(DummyContract.Commands.Create(), state.participants.map { it.owningKey })
                val builder = TransactionType.General.Builder(notary = notary).withItems(state, command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val stx = subFlow(CollectSignaturesFlow(ptx))
                val ftx = subFlow(FinalityFlow(stx)).single()

                return ftx
            }
        }

        @InitiatedBy(TestFlowTwo.Initiator::class)
        class Responder(val otherParty: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable override fun call(): SignedTransaction {
                val flow = object : SignTransactionFlow(otherParty) {
                    @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val tx = stx.tx
                        "There should only be one output state" using (tx.outputs.size == 1)
                        "There should only be one output state" using (tx.inputs.isEmpty())
                        val magicNumberState = tx.outputs.single().data as DummyContract.MultiOwnerState
                        "Must be 1337 or greater" using (magicNumberState.magicNumber >= 1337)
                    }
                }

                val stx = subFlow(flow)

                return waitForLedgerCommit(stx.id)
            }
        }
    }

    @Test
    fun `successfully collects two signatures`() {
        registerFlowOnAllNodes(TestFlowTwo.Responder::class)
        val magicNumber = 1337
        val parties = listOf(a.info.legalIdentity, b.info.legalIdentity, c.info.legalIdentity)
        val state = DummyContract.MultiOwnerState(magicNumber, parties)
        val flow = a.services.startFlow(TestFlowTwo.Initiator(state))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifySignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `no need to collect any signatures`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, a.info.legalIdentity.ref(1))
        val ptx = a.services.signInitialTransaction(onePartyDummyContract)
        val flow = a.services.startFlow(CollectSignaturesFlow(ptx))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifySignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `fails when not signed by initiator`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, a.info.legalIdentity.ref(1))
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
                a.info.legalIdentity.ref(1),
                b.info.legalIdentity.ref(2),
                b.info.legalIdentity.ref(3))
        val signedByA = a.services.signInitialTransaction(twoPartyDummyContract)
        val signedByBoth = b.services.addSignature(signedByA)
        val flow = a.services.startFlow(CollectSignaturesFlow(signedByBoth))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        println(result.tx)
        println(result.sigs)
    }
}

