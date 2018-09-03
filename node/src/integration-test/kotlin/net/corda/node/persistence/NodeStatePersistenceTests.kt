package net.corda.node.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testMessage.MESSAGE_CONTRACT_PROGRAM_ID
import net.corda.testMessage.Message
import net.corda.testMessage.MessageContract
import net.corda.testMessage.MessageState
import net.corda.testing.core.singleIdentity
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.junit.Assume.assumeFalse
import org.junit.ClassRule
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NodeStatePersistenceTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }
    @Test
    fun `persistent state survives node restart`() {
        val user = User("mark", "dadada", setOf(startFlow<SendMessageFlow>(), invokeRpc("vaultQuery")))
        val message = Message("Hello world!")
        val stateAndRef: StateAndRef<MessageState>? = driver(DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = isQuasarAgentSpecified(),
                extraCordappPackagesToScan = listOf(MessageState::class.packageName)
        )) {
            val nodeName = {
                val nodeHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::SendMessageFlow, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                nodeName
            }()

            val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user)).getOrThrow()
            val result = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                val page = it.proxy.vaultQuery(MessageState::class.java)
                page.states.singleOrNull()
            }
            nodeHandle.stop()
            result
        }
        assertNotNull(stateAndRef)
        val retrievedMessage = stateAndRef!!.state.data.message
        assertEquals(message, retrievedMessage)
    }

    @Test
    fun `persistent state survives node restart without reinitialising database schema`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        val user = User("mark", "dadada", setOf(startFlow<SendMessageFlow>(), invokeRpc("vaultQuery")))
        val message = Message("Hello world!")
        val stateAndRef: StateAndRef<MessageState>? = driver(DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = isQuasarAgentSpecified(),
                extraCordappPackagesToScan = listOf(MessageState::class.packageName)
        )) {
            val nodeName = {
                val nodeHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::SendMessageFlow, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                nodeName
            }()

            val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user), customOverrides = mapOf("devMode" to "false")).getOrThrow()
            val result = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                val page = it.proxy.vaultQuery(MessageState::class.java)
                page.states.singleOrNull()
            }
            nodeHandle.stop()
            result
        }
        assertNotNull(stateAndRef)
        val retrievedMessage = stateAndRef!!.state.data.message
        assertEquals(message, retrievedMessage)
    }
}

fun isQuasarAgentSpecified(): Boolean {
    val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    return jvmArgs.any { it.startsWith("-javaagent:") && it.contains("quasar") }
}

@StartableByRPC
class SendMessageFlow(private val message: Message, private val notary: Party) : FlowLogic<SignedTransaction>() {
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

        val messageState = MessageState(message = message, by = ourIdentity)
        val txCommand = Command(MessageContract.Commands.Send(), messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, MESSAGE_CONTRACT_PROGRAM_ID), txCommand)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(signedTx, FINALISING_TRANSACTION.childProgressTracker()))
    }
}