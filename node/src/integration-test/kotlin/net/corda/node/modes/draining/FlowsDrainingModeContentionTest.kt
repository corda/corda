package net.corda.node.modes.draining

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.node.services.Permissions
import net.corda.MESSAGE_CONTRACT_PROGRAM_ID
import net.corda.Message
import net.corda.MessageContract
import net.corda.MessageState
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class FlowsDrainingModeContentionTest {

    private val portAllocation = PortAllocation.Incremental(10000)
    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    private var executor: ScheduledExecutorService? = null

    companion object {
        private val logger = loggerFor<P2PFlowsDrainingModeTest>()
    }

    @Before
    fun setup() {
        executor = Executors.newSingleThreadScheduledExecutor()
    }

    @After
    fun cleanUp() {
        executor!!.shutdown()
    }

    @Test
    fun `draining mode does not deadlock with acks between 2 nodes`() {

        val message = "Ground control to Major Tom"

        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = portAllocation, extraCordappPackagesToScan = listOf(MessageState::class.packageName))) {

            val nodeA = startNode(rpcUsers = users).getOrThrow()
            val nodeB = startNode(rpcUsers = users).getOrThrow()
            defaultNotaryNode.getOrThrow()

            val flow = nodeA.rpc.startFlow(::ProposeTransactionAndWaitForCommit, message, nodeB.nodeInfo.singleIdentity(), defaultNotaryIdentity)
            val committedTx = flow.returnValue.getOrThrow()

            committedTx.inputs
            committedTx.tx.outputs
            assertThat(committedTx.tx.outputsOfType<MessageState>().single().message.value).isEqualTo(message)
        }
    }
}

@StartableByRPC
@InitiatingFlow
class ProposeTransactionAndWaitForCommit(private val data: String, private val counterParty: Party, private val notary: Party) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val session = initiateFlow(counterParty)
        val transaction = TransactionBuilder(notary)

        val messageState = MessageState(message = Message(data), by = ourIdentity)
        val command = Command(MessageContract.Commands.Send(), messageState.participants.map { it.owningKey })
        transaction.withItems(StateAndContract(messageState, MESSAGE_CONTRACT_PROGRAM_ID), command)
        val signedTx = serviceHub.signInitialTransaction(transaction)
        subFlow(SendTransactionFlow(session, signedTx))
        return waitForLedgerCommit(signedTx.id)
    }
}

@InitiatedBy(ProposeTransactionAndWaitForCommit::class)
class SignTransactionTriggerDrainingModeAndFinality(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val tx = subFlow(ReceiveTransactionFlow(session))
        logger.info("Got transaction from counterParty.")
        val signedTx = serviceHub.addSignature(tx)
        // TODO MS fix this
//        alice.services.nodeProperties.flowsDrainingMode.setEnabled(true)
        subFlow(FinalityFlow(signedTx, setOf(session.counterparty)))
    }
}