package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import kotlin.test.assertTrue
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class FlowOperatorTests {

    companion object {
        val log = contextLogger()
        val aliceX500Name = CordaX500Name("Alice", "AliceCorp", "GB")
        val bobX500Name = CordaX500Name("Bob", "BobCorp", "GB")
        val carolX500Name = CordaX500Name("Carol", "CarolCorp", "GB")
        val daveX500Name = CordaX500Name("Dave", "DaveCorp", "GB")
        val semaphores = mutableMapOf<String, Map<String, Pair<Semaphore, Semaphore>>>()

        fun newSemaphore(vararg parties: Party): String {
            if(parties.isEmpty()) {
                error("Specify at least one party")
            }
            val marker = UUID.randomUUID().toString()
            val partySemaphores = mutableMapOf<String, Pair<Semaphore, Semaphore>>()
            parties.forEach {
                val semaphore1 = Semaphore(1)
                semaphore1.acquire() // initial state is to block
                val semaphore2 = Semaphore(1)
                semaphore2.acquire() // initial state is to block
                partySemaphores[it.name.toString()] = Pair(semaphore1, semaphore2)
            }
            semaphores[marker] = partySemaphores
            return marker
        }

        fun Map<String, Map<String, Pair<Semaphore, Semaphore>>>.waitToResumeAccepting(marker: String, party: Party) {
            if(!getValue(marker).getValue(party.name.toString()).first.tryAcquire(5, TimeUnit.SECONDS)) {
                error("(Party: $party) Waiting too long for the signal to continue...")
            }
            log.info("Party $party continues accepting the flow")
        }

        fun Map<String, Map<String, Pair<Semaphore, Semaphore>>>.enteredAccepting(marker: String, party: Party) {
            log.info("Party $party entering accepting the flow")
            getValue(marker).getValue(party.name.toString()).second.release()
        }

        fun Map<String, Map<String, Pair<Semaphore, Semaphore>>>.waitToContinueTest(marker: String, vararg parties: Party) {
            parties.forEach {
                if(!getValue(marker).getValue(it.name.toString()).second.tryAcquire(5, TimeUnit.SECONDS)) {
                    error("(Party: $it) Waiting too long to kick off accepting flow...")
                }
            }
        }

        fun Map<String, Map<String, Pair<Semaphore, Semaphore>>>.resumeAccepting(marker: String, vararg parties: Party) {
            parties.forEach {
                getValue(marker).getValue(it.name.toString()).first.release()
            }
        }
    }

    lateinit var mockNet: InternalMockNetwork
    lateinit var aliceNode: TestStartedNode
    lateinit var aliceParty: Party
    lateinit var bobNode: TestStartedNode
    lateinit var bobParty: Party
    lateinit var carolNode: TestStartedNode
    lateinit var carolParty: Party
    lateinit var daveNode: TestStartedNode
    lateinit var daveParty: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(
                threadPerNode = true,
                cordappsForAllNodes = listOf(enclosedCordapp())
        )
        aliceNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = aliceX500Name
        ))
        bobNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = bobX500Name
        ))
        carolNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = carolX500Name
        ))
        daveNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = daveX500Name
        ))
        mockNet.startNodes()
        aliceParty = aliceNode.info.legalIdentities.first()
        bobParty = bobNode.info.legalIdentities.first()
        carolParty = carolNode.info.legalIdentities.first()
        daveParty = daveNode.info.legalIdentities.first()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should return all parties for flows which are waiting for other party to process`() {
        val otherParties = arrayOf(bobParty)
        val queryParties = listOf(aliceParty, bobParty, carolParty, daveParty)
        val marker = newSemaphore(*otherParties)
        val future = aliceNode.services.startFlow(TestInitiatorFlow(marker, *otherParties)).resultFuture
        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        semaphores.waitToContinueTest(marker, *otherParties)

        var result1 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
        assertEquals(1, result1.size)
        assertEquals(1, result1.first().waitingForParties.size)
        assertTrue(result1.first().waitingForParties.any { it.name == bobX500Name })

        semaphores.resumeAccepting(marker, *otherParties)
        future.getOrThrow(5.seconds)

        var result2 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
        assertEquals(0, result2.size)
    }

    @BelongsToContract(TestContract::class)
    class TestState(val marker: String, val me: Party, vararg val otherParties: Party) : ContractState {
        override val participants: List<AbstractParty> = listOf(me, *otherParties)
    }

    class TestContract : Contract {
        companion object {
            @JvmStatic
            val ID = "net.corda.node.services.statemachine.FlowOperatorTests\$TestContract"
        }

        override fun verify(tx: LedgerTransaction) {
        }

        interface Commands : CommandData {
            class Create : Commands
        }
    }

    @InitiatingFlow
    class TestInitiatorFlow(val semaphore: String, vararg val otherParties: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val state = TestState(semaphore, serviceHub.myInfo.legalIdentities.first(), *otherParties)
            val command = Command(TestContract.Commands.Create(), state.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(state, TestContract.ID)
                    .addCommand(command)
            txBuilder.verify(serviceHub)
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            val otherPartySessions = mutableSetOf<FlowSession>()
            otherParties.forEach {
                log.info("Creating session for $it")
                otherPartySessions.add(initiateFlow(it))
            }
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartySessions))
            subFlow(FinalityFlow(fullySignedTx, otherPartySessions))
        }
    }

    @InitiatedBy(TestInitiatorFlow::class)
    class TestAcceptorFlow(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val marker = (stx.tx.outputStates[0] as TestState).marker
                    val me = serviceHub.myInfo.legalIdentities.first()
                    semaphores.enteredAccepting(marker, me)
                    semaphores.waitToResumeAccepting(marker, me)
                    return@requireThat
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}