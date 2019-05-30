package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.node.testing.MESSAGE_CHAIN_CONTRACT_PROGRAM_ID
import net.corda.node.testing.MessageChainContract
import net.corda.node.testing.MessageChainState
import net.corda.node.testing.MessageData
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ObserverNodeTransactionTests {
    private lateinit var mockNet: InternalMockNetwork

    @Before
    fun start() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(cordappWithPackages("net.corda.node.testing"), enclosedCordapp()),
                networkSendManuallyPumped = false,
                threadPerNode = true
        )
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    fun buildTransactionChain(initialMessage: MessageData, chainLength: Int, node: TestStartedNode, notary: Party) {
        node.services.startFlow(StartMessageChainFlow(initialMessage, notary)).resultFuture.getOrThrow()
        var result = node.services.vaultService.queryBy(MessageChainState::class.java).states.singleOrNull {
            it.state.data.message.value.startsWith(initialMessage.value)
        }

        for (_i in 0.until(chainLength -1 )) {
            node.services.startFlow(ContinueMessageChainFlow(result!!, notary)).resultFuture.getOrThrow()
            result = node.services.vaultService.queryBy(MessageChainState::class.java).states.singleOrNull {
                it.state.data.message.value.startsWith(initialMessage.value)
            }
        }
    }

    fun sendTransactionToObserver(transactionIdx: Int, node: TestStartedNode, regulator: TestStartedNode) {
        val transactionList = node.services.validatedTransactions.track().snapshot
        node.services.startFlow(ReportToCounterparty(regulator.info.singleIdentity(), transactionList[transactionIdx])).resultFuture.getOrThrow()
    }

    fun sendTransactionToObserverOnlyRelevant(transactionIdx: Int, node: TestStartedNode, regulator: TestStartedNode) {
        val transactionList = node.services.validatedTransactions.track().snapshot
        node.services.startFlow(SendTransaction(regulator.info.singleIdentity(), transactionList[transactionIdx])).resultFuture.getOrThrow()
    }

    fun checkObserverTransactions(expectedMessage: MessageData, regulator: TestStartedNode, numStates: Int = 1) {
        val regulatorStates = regulator.services.vaultService.queryBy(MessageChainState::class.java).states.filter {
            it.state.data.message.value.startsWith(expectedMessage.value[0])
        }

        assertNotNull(regulatorStates, "Could not find any regulator states")
        assertEquals(numStates, regulatorStates.size, "Incorrect number of unconsumed regulator states")
        for (state in regulatorStates) {
            val retrievedMessage = state.state.data.message
            assertEquals(expectedMessage, retrievedMessage, "Final unconsumed regulator state is incorrect")
        }
    }


    @Test
    fun `Broadcasting an old transaction does not cause 2 unconsumed states`() {
        val node = mockNet.createPartyNode(ALICE_NAME)
        val regulator = mockNet.createPartyNode(BOB_NAME)
        val notary = mockNet.defaultNotaryIdentity
        regulator.registerInitiatedFlow(ReceiveReportedTransaction::class.java)

        // Check that sending an old transaction doesn't result in a new unconsumed state
        val message = MessageData("A")
        buildTransactionChain(message, 4, node, notary)
        sendTransactionToObserver(3, node, regulator)
        sendTransactionToObserver(1, node, regulator)
        val outputMessage = MessageData("AAAA")
        checkObserverTransactions(outputMessage, regulator)
    }

    @Test
    fun `Non relevant states are recorded if transaction is re-received with new states to record`() {
        val node = mockNet.createPartyNode(ALICE_NAME)
        val regulator = mockNet.createPartyNode(BOB_NAME)
        val notary = mockNet.defaultNotaryIdentity
        regulator.registerInitiatedFlow(ReceiveReportedTransaction::class.java)
        regulator.registerInitiatedFlow(ReceiveTransaction::class.java)

        val message = MessageData("A")
        buildTransactionChain(message, 4, node, notary)
        sendTransactionToObserverOnlyRelevant(3, node, regulator)
        sendTransactionToObserver(3, node, regulator)
        val outputMessage = MessageData("AAAA")
        checkObserverTransactions(outputMessage, regulator)
    }

    @Test
    fun `Re-recording a transaction adds non-relevant states`() {
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val bob = mockNet.createPartyNode(BOB_NAME)
        val notary = mockNet.defaultNotaryIdentity
        bob.registerInitiatedFlow(ReceiveSplitMessagesFlow::class.java)
        bob.registerInitiatedFlow(ReceiveReportedTransaction::class.java)

        val message = MessageData("AA")
        alice.services.startFlow(SplitMessagesFlow(message, bob.info.singleIdentity(), notary)).resultFuture.getOrThrow()
        checkObserverTransactions(message, bob)
        sendTransactionToObserver(0, alice, bob)
        checkObserverTransactions(message, bob, 2)
    }

    @Test
    fun `Re-recording a transaction at only relevant does not cause failures`() {
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val bob = mockNet.createPartyNode(BOB_NAME)
        val notary = mockNet.defaultNotaryIdentity
        bob.registerInitiatedFlow(ReceiveSplitMessagesFlow::class.java)
        bob.registerInitiatedFlow(ReceiveReportedTransaction::class.java)

        val message = MessageData("AA")
        alice.services.startFlow(SplitMessagesFlow(message, bob.info.singleIdentity(), notary)).resultFuture.getOrThrow()
        checkObserverTransactions(message, bob)
        sendTransactionToObserverOnlyRelevant(0, alice, bob)
        checkObserverTransactions(message, bob, 1)
    }

    @Test
    fun `Recording a transaction twice at all visible works`() {
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val bob = mockNet.createPartyNode(BOB_NAME)
        val notary = mockNet.defaultNotaryIdentity
        bob.registerInitiatedFlow(ReceiveSplitMessagesFlow::class.java)
        bob.registerInitiatedFlow(ReceiveReportedTransaction::class.java)

        val message = MessageData("AA")
        alice.services.startFlow(SplitMessagesFlow(message, bob.info.singleIdentity(), notary)).resultFuture.getOrThrow()
        checkObserverTransactions(message, bob)
        sendTransactionToObserverOnlyRelevant(0, alice, bob)
        checkObserverTransactions(message, bob, 1)
        sendTransactionToObserver(0, alice, bob)
        checkObserverTransactions(message, bob, 2)
        sendTransactionToObserver(0, alice, bob)
        checkObserverTransactions(message, bob, 2)
        sendTransactionToObserverOnlyRelevant(0, alice, bob)
        checkObserverTransactions(message, bob, 2)
    }

    @StartableByRPC
    class StartMessageChainFlow(private val message: MessageData, private val notary: Party) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on the message.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(GENERATING_TRANSACTION, VERIFYING_TRANSACTION, SIGNING_TRANSACTION, FINALISING_TRANSACTION)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_TRANSACTION

            val messageState = MessageChainState(message = message, by = ourIdentity)
            val txCommand = Command(MessageChainContract.Commands.Send(), messageState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, MESSAGE_CHAIN_CONTRACT_PROGRAM_ID), txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()

            progressTracker.currentStep = SIGNING_TRANSACTION
            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(signedTx, emptyList(), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @StartableByRPC
    class ContinueMessageChainFlow(private val stateRef: StateAndRef<MessageChainState>,
                                   private val notary: Party) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on the message.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(GENERATING_TRANSACTION, VERIFYING_TRANSACTION, SIGNING_TRANSACTION, FINALISING_TRANSACTION)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_TRANSACTION

            val oldMessageState = stateRef.state.data
            val messageState = MessageChainState(MessageData(oldMessageState.message.value + "A"),
                    ourIdentity,
                    stateRef.state.data.linearId)
            val txCommand = Command(MessageChainContract.Commands.Send(), messageState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary).withItems(
                    StateAndContract(messageState, MESSAGE_CHAIN_CONTRACT_PROGRAM_ID),
                    txCommand,
                    stateRef)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()

            progressTracker.currentStep = SIGNING_TRANSACTION
            val signedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(signedTx, emptyList(), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SplitMessagesFlow(private val message: MessageData,
                            private val counterparty: Party,
                            private val notary: Party): FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on the message.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(GENERATING_TRANSACTION, VERIFYING_TRANSACTION, SIGNING_TRANSACTION, FINALISING_TRANSACTION)
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_TRANSACTION

            val messageState = MessageChainState(message = message, by = ourIdentity)
            val otherPartyState = MessageChainState(message = message, by = counterparty)
            val txCommand = Command(MessageChainContract.Commands.Split(), listOf(ourIdentity.owningKey, counterparty.owningKey))
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(messageState)
                    .addOutputState(otherPartyState)
                    .addCommand(txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()

            progressTracker.currentStep = SIGNING_TRANSACTION
            val signedTx = serviceHub.signInitialTransaction(txBuilder)
            val session = initiateFlow(counterparty)

            val stx = subFlow(CollectSignaturesFlow(signedTx, listOf(session)))

            progressTracker.currentStep = FINALISING_TRANSACTION
            val finalStx = subFlow(FinalityFlow(stx, listOf(session), FINALISING_TRANSACTION.childProgressTracker()))
            session.receive<Unit>()
            return finalStx
        }
    }

    @InitiatedBy(SplitMessagesFlow::class)
    class ReceiveSplitMessagesFlow(private val otherSideSession: FlowSession): FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(otherSideSession) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {

                }
            }
            subFlow(flow)
            val stx = subFlow(ReceiveFinalityFlow(otherSideSession))
            otherSideSession.send(Unit)
            return stx
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class ReportToCounterparty(private val regulator: Party,
                               private val signedTx: SignedTransaction) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(regulator)
            subFlow(SendTransactionFlow(session, signedTx))
            session.receive<Unit>()
        }
    }

    @InitiatedBy(ReportToCounterparty::class)
    class ReceiveReportedTransaction(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(otherSideSession, true, StatesToRecord.ALL_VISIBLE))
            otherSideSession.send(Unit)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class SendTransaction(private val regulator: Party,
                          private val signedTx: SignedTransaction) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(regulator)
            subFlow(SendTransactionFlow(session, signedTx))
            session.receive<Unit>()
        }
    }

    @InitiatedBy(SendTransaction::class)
    class ReceiveTransaction(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(otherSideSession, true, StatesToRecord.ONLY_RELEVANT))
            otherSideSession.send(Unit)
        }
    }
}
