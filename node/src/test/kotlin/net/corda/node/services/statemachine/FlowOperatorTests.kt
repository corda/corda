package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.flows.registerCordappFlowFactory
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
import kotlin.test.assertNotEquals

class FlowOperatorTests {

    companion object {
        val log = contextLogger()
        val aliceX500Name = CordaX500Name("Alice", "AliceCorp", "GB")
        val bobX500Name = CordaX500Name("Bob", "BobCorp", "GB")
        val carolX500Name = CordaX500Name("Carol", "CarolCorp", "GB")
        val daveX500Name = CordaX500Name("Dave", "DaveCorp", "GB")
        val offlineX500Name = CordaX500Name("Offline", "OfflineCorp", "GB")
        val acceptorInstructions = mutableMapOf<String, Map<String, AcceptorInstructions>>()

        fun newInstructions(vararg instructions: Instructions) =
                newInstructions(UUID.randomUUID().toString(), *instructions)

        fun newInstructions(marker: String, vararg instructions: Instructions): String {
            if(instructions.isEmpty()) {
                error("Specify at least one party")
            }
            val partySemaphores = mutableMapOf<String, AcceptorInstructions>()
            instructions.forEach {
                val semaphore1 = Semaphore(1)
                semaphore1.acquire() // initial state is to block
                val semaphore2 = Semaphore(1)
                semaphore2.acquire() // initial state is to block
                partySemaphores[it.name] = AcceptorInstructions(
                        it.name,
                        it.fail,
                        Pair(semaphore1, semaphore2)
                )
            }
            this.acceptorInstructions[marker] = partySemaphores
            return marker
        }

        fun SignedTransaction.getMyInstructions(serviceHub: ServiceHub) =
                acceptorInstructions
                    .getValue((tx.outputStates.first { it is TestState } as TestState).marker)
                    .getValue(serviceHub.myInfo.legalIdentities.first().name.toString())

        fun String.getMyInstructions(serviceHub: ServiceHub) =
                acceptorInstructions
                        .getValue(this)
                        .getValue(serviceHub.myInfo.legalIdentities.first().name.toString())

        fun AcceptorInstructions.waitToCompleteAccepting() {
            log.info("Party $name waiting to resume accepting the flow")
            if(!semaphores.first.tryAcquire(15, TimeUnit.SECONDS)) {
                error("(Party: $name) Waiting too long for the signal to continue...")
            }
            log.info("Party $name resumed the flow")
        }

        fun AcceptorInstructions.enteredAcceptor() {
            semaphores.second.release()
            log.info("Party $name entered the flow")
        }

        fun AcceptorInstructions.process() {
            enteredAcceptor()
            "Should fail when requested" using ( !fail )
            waitToCompleteAccepting()
        }

        fun Map<String, Map<String, AcceptorInstructions>>.waitToContinueTest(marker: String, vararg parties: Party) {
            log.info("Started waiting to continue tests for ${parties.joinToString(";")}")
            parties.forEach {
                if(!getValue(marker).getValue(it.name.toString()).semaphores.second.tryAcquire(15, TimeUnit.SECONDS)) {
                    error("(Party: $it) Waiting too long to kick off accepting flow...")
                }
            }
            log.info("Continue tests for ${parties.joinToString(";")}")
        }

        fun Map<String, Map<String, AcceptorInstructions>>.resumeAccepting(marker: String, vararg parties: Party) {
            parties.forEach {
                getValue(marker).getValue(it.name.toString()).semaphores.first.release()
            }
            log.info("Resumed accepting for ${parties.joinToString(";")}")
        }
    }

    lateinit var mockNet: InternalMockNetwork
    lateinit var aliceNode: TestStartedNode
    private lateinit var aliceParty: Party
    lateinit var bobNode: TestStartedNode
    private lateinit var bobParty: Party
    lateinit var carolNode: TestStartedNode
    private lateinit var carolParty: Party
    lateinit var daveNode: TestStartedNode
    lateinit var daveParty: Party
    lateinit var offlineParty: Party

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
        val offlineNode = mockNet.createNode(InternalMockNodeParameters(
                legalName = offlineX500Name
        ))
        mockNet.startNodes()
        aliceParty = aliceNode.info.legalIdentities.first()
        bobParty = bobNode.info.legalIdentities.first()
        carolParty = carolNode.info.legalIdentities.first()
        daveParty = daveNode.info.legalIdentities.first()
        offlineParty = offlineNode.info.legalIdentities.first()
        offlineNode.dispose()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `getFlowsCurrentlyWaitingForParties should return all flows which are waiting for other party to process`() {
        val otherParties = arrayOf(bobParty, daveParty)
        val queryParties = listOf(aliceParty, bobParty, carolParty, daveParty)
        val marker = newInstructions(*otherParties.map {
            Instructions(it.name, false)
        }.toTypedArray())
        val bobFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, bobParty)).resultFuture
        val daveFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, daveParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        acceptorInstructions.waitToContinueTest(marker, *otherParties)
        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
            assertEquals(2, result1.size)
            assertEquals(1, result1.first().waitingForParties.size)
            assertEquals(1, result1.last().waitingForParties.size)
            val firstName = result1.first().waitingForParties.first().name
            val lastName = result1.last().waitingForParties.first().name
            assertNotEquals(firstName, lastName)
            if(firstName == bobX500Name) {
                assertEquals(daveX500Name, lastName)
            } else {
                assertEquals(daveX500Name, firstName)
                assertEquals(bobX500Name, lastName)
            }
        } finally {
            acceptorInstructions.resumeAccepting(marker, *otherParties)
            bobFuture.getOrThrow(5.seconds)
            daveFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
        assertEquals(0, result2.size)
    }

    @Test
    fun `getFlowsCurrentlyWaitingForParties should return only requested by party flows which are waiting for other party to process`() {
        val otherParties = arrayOf(bobParty, daveParty)
        val marker = newInstructions(*otherParties.map {
            Instructions(it.name, false)
        }.toTypedArray())
        val bobFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, bobParty)).resultFuture
        val daveFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, daveParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        acceptorInstructions.waitToContinueTest(marker, *otherParties)
        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getFlowsCurrentlyWaitingForParties(listOf(daveParty))
            assertEquals(1, result1.size)
            assertEquals(1, result1.first().waitingForParties.size)
            assertEquals(daveX500Name, result1.first().waitingForParties.first().name)
        } finally {
            acceptorInstructions.resumeAccepting(marker, *otherParties)
            bobFuture.getOrThrow(5.seconds)
            daveFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getFlowsCurrentlyWaitingForParties(listOf(aliceParty, bobParty, carolParty, daveParty))
        assertEquals(0, result2.size)
    }

    @Test
    fun `getFlowsCurrentlyWaitingForParties should return only flows which are waiting for other party to process and not in the hospital`() {
        val otherParties = arrayOf(bobParty, daveParty)
        val queryParties = listOf(aliceParty, bobParty, carolParty, daveParty)
        val marker = newInstructions(
                Instructions(
                        bobX500Name,
                        true
                ),
                Instructions(
                        daveX500Name,
                        false
                ))
        val bobFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, bobParty)).resultFuture
        val daveFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, daveParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        acceptorInstructions.waitToContinueTest(marker, *otherParties)
        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
            assertEquals(1, result1.size)
            assertEquals(1, result1.first().waitingForParties.size)
            assertEquals(daveX500Name, result1.first().waitingForParties.first().name)
        } finally {
            acceptorInstructions.resumeAccepting(marker, *otherParties)
            try {
                bobFuture.get(5, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                // ignore as it's expected to fail
            }
            daveFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
        assertEquals(0, result2.size)
    }

    @Test
    fun `getFlowsCurrentlyWaitingForParties should return only flows which are waiting more than 4 seconds for other party to process`() {
        val otherParties = arrayOf(bobParty, daveParty)
        val queryParties = listOf(aliceParty, bobParty, carolParty, daveParty)
        val marker = newInstructions(
                Instructions(
                        bobX500Name,
                        false
                ),
                Instructions(
                        daveX500Name,
                        false
                ))
        val bobFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, bobParty)).resultFuture
        acceptorInstructions.waitToContinueTest(marker, bobParty)
        Thread.sleep(4500) // let time to settle all flow states properly
        val daveFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, daveParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            log.info("Will querying for flows waiting for over 4 seconds")
            val result1 = cut.getFlowsCurrentlyWaitingForParties(queryParties, 4.seconds)
            assertEquals(1, result1.size)
            assertEquals(1, result1.first().waitingForParties.size)
            assertEquals(bobX500Name, result1.first().waitingForParties.first().name)
        } finally {
            acceptorInstructions.resumeAccepting(marker, *otherParties)
            try {
                bobFuture.get(5, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                // ignore as it's expected to fail
            }
            daveFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
        assertEquals(0, result2.size)
    }

    @Test
    fun `getFlowsCurrentlyWaitingForPartiesGrouped should return all flows which are waiting for other party to process grouped by party`() {
        val otherParties = arrayOf(bobParty, daveParty)
        val queryParties = listOf(aliceParty, bobParty, carolParty, daveParty)
        val marker = newInstructions(*otherParties.map {
            Instructions(it.name, false)
        }.toTypedArray())
        val bobFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, bobParty)).resultFuture
        val daveFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, daveParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        acceptorInstructions.waitToContinueTest(marker, *otherParties)
        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getFlowsCurrentlyWaitingForPartiesGrouped(queryParties)
            assertEquals(2, result1.size)
            assertEquals(1, result1.getValue(bobParty).size)
            assertEquals(1, result1.getValue(bobParty).first().waitingForParties.size)
            assertEquals(bobX500Name, result1.getValue(bobParty).first().waitingForParties.first().name)
            assertEquals(1, result1.getValue(daveParty).size)
            assertEquals(1, result1.getValue(daveParty).first().waitingForParties.size)
            assertEquals(daveX500Name, result1.getValue(daveParty).first().waitingForParties.first().name)
        } finally {
            acceptorInstructions.resumeAccepting(marker, *otherParties)
            bobFuture.getOrThrow(5.seconds)
            daveFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
        assertEquals(0, result2.size)
    }

    @Test
    fun `getWaitingFlows should return all flow state machines which are waiting for other party to process`() {
        val otherParties = arrayOf(bobParty, daveParty)
        val marker = newInstructions(*otherParties.map {
            Instructions(it.name, false)
        }.toTypedArray())
        val bobFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, bobParty)).resultFuture
        val daveFuture = aliceNode.services.startFlow(TestInitiatorFlow(marker, daveParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        acceptorInstructions.waitToContinueTest(marker, *otherParties)
        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getWaitingFlows().toList()
            assertEquals(2, result1.size)
        } finally {
            acceptorInstructions.resumeAccepting(marker, *otherParties)
            bobFuture.getOrThrow(5.seconds)
            daveFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getWaitingFlows().toList()
        assertEquals(0, result2.size)
    }

    @Test
    fun `getWaitingFlows should return only requested by id flows which are waiting for other party to process`() {
        val otherParties = arrayOf(bobParty, daveParty, carolParty)
        val marker = newInstructions(
                Instructions(
                        bobX500Name,
                        true
                ),
                Instructions(
                        daveX500Name,
                        false
                ),
                Instructions(
                        carolX500Name,
                        false
                ))
        val bobStart = aliceNode.services.startFlow(TestInitiatorFlow(marker, bobParty))
        val daveStart = aliceNode.services.startFlow(TestInitiatorFlow(marker, daveParty))
        val carolStart = aliceNode.services.startFlow(TestInitiatorFlow(marker, carolParty))

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        acceptorInstructions.waitToContinueTest(marker, *otherParties)
        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getWaitingFlows(listOf(bobStart.id, daveStart.id, carolStart.id)).toList()
            assertEquals(2, result1.size)
            assertEquals(1, result1.first().waitingForParties.size)
            assertEquals(1, result1.last().waitingForParties.size)
            val firstName = result1.first().waitingForParties.first().name
            val lastName = result1.last().waitingForParties.first().name
            assertNotEquals(firstName, lastName)
            if(firstName == daveX500Name) {
                assertEquals(carolX500Name, lastName)
            } else {
                assertEquals(carolX500Name, firstName)
                assertEquals(daveX500Name, lastName)
            }
        } finally {
            acceptorInstructions.resumeAccepting(marker, *otherParties)
            try {
                bobStart.resultFuture.get(5, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                // expected to fail
            }
            daveStart.resultFuture.getOrThrow(5.seconds)
            carolStart.resultFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getWaitingFlows().toList()
        assertEquals(0, result2.size)
    }

    @Test
    fun `should return all flows which are waiting for sending from other party`() {
        bobNode.registerCordappFlowFactory(TestReceiveFlow::class) { TestInitiatedSendFlow("Hello", it) }
        val queryParties = listOf(aliceParty, bobParty, carolParty, daveParty)
        val marker = newInstructions(
                "TestInitiatedSendFlow",
                Instructions(
                        bobX500Name,
                        false
                )
        )
        val bobFuture = aliceNode.services.startFlow(TestReceiveFlow(bobParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        acceptorInstructions.waitToContinueTest(marker, bobParty)
        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
            assertEquals(1, result1.size)
            assertEquals(1, result1.first().waitingForParties.size)
            assertEquals(bobX500Name, result1.first().waitingForParties.first().name)
        } finally {
            acceptorInstructions.resumeAccepting(marker, bobParty)
            bobFuture.getOrThrow(5.seconds)
        }

        val result2 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
        assertEquals(0, result2.size)
    }

    @Test
    fun `should return all flows which are waiting for getting info about other party`() {
        val queryParties = listOf(aliceParty, bobParty, carolParty, daveParty, offlineParty)

        val offlineFuture = aliceNode.services.startFlow(TestSendStuckInGetFlowInfoFlow("Hello", offlineParty)).resultFuture

        val cut = FlowOperator(aliceNode.smm, aliceNode.services.clock)

        Thread.sleep(1000) // let time to settle all flow states properly

        try {
            val result1 = cut.getFlowsCurrentlyWaitingForParties(queryParties)
            assertEquals(1, result1.size)
            assertEquals(1, result1.first().waitingForParties.size)
            assertEquals(offlineX500Name, result1.first().waitingForParties.first().name)
        } finally {
            try {
                offlineFuture.get(100, TimeUnit.MILLISECONDS)
            } catch (e: Throwable) {
                // expected to fail
            }
        }
    }

    open class Instructions(
            x500Name: CordaX500Name,
            val fail: Boolean
    ) {
        val name: String = x500Name.toString()
    }

    class AcceptorInstructions(
            val name: String,
            val fail: Boolean,
            val semaphores: Pair<Semaphore, Semaphore>
    )

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
    class TestInitiatorFlow(val marker: String, vararg val otherParties: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val state = TestState(marker, serviceHub.myInfo.legalIdentities.first(), *otherParties)
            val command = Command(TestContract.Commands.Create(), state.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(state, TestContract.ID)
                    .addCommand(command)
            txBuilder.verify(serviceHub)
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            val otherPartySessions = mutableSetOf<FlowSession>()
            otherParties.forEach {
                log.info("Creating flow session for $it")
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
                    stx.getMyInstructions(serviceHub).process()
                    return@requireThat
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

    @InitiatingFlow
    class TestReceiveFlow(private vararg val otherParties: Party) : FlowLogic<Unit>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call() {
            otherParties.forEach { initiateFlow(it).receive<String>() }
        }
    }

    class TestInitiatedSendFlow(private val payload: Any, private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            "TestInitiatedSendFlow".getMyInstructions(serviceHub).process()
            otherPartySession.send(payload)
        }
    }

    @InitiatingFlow
    class TestSendStuckInGetFlowInfoFlow(private val payload: String, private vararg val otherParties: Party) : FlowLogic<FlowInfo>() {
        init {
            require(otherParties.isNotEmpty())
        }

        @Suspendable
        override fun call(): FlowInfo {
            val flowInfos = otherParties.map {
                val session = initiateFlow(it)
                session.send(payload)
                session.getCounterpartyFlowInfo()
            }.toList()
            return flowInfos.first()
        }
    }
}