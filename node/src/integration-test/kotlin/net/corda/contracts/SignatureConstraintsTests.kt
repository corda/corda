package net.corda.contracts

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testMessage.Message
import net.corda.testMessage.MessageState
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappForPackages
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SignatureConstrainsTests {

    private val base = cordappForPackages(MessageState::class.packageName, DummyMessageContract::class.packageName)
    private val oldCordapp = base.withImplementationVersion("2.0")
    private val newCordapp = base.withImplementationVersion("3.0")
    private val user = User("mark", "dadada", setOf(startFlow<CreateMessage>(), startFlow<ConsumeMessage>(), invokeRpc("vaultQuery")))
    private val message = Message("Hello world!")
    private val transformetMessage = Message(message.value + "A")
    private val driverParameters = DriverParameters(
            inMemoryDB = false,
            startNodesInProcess = isQuasarAgentSpecified(),
            networkParameters = testNetworkParameters(notaries = emptyList(), minimumPlatformVersion = 4))

    //@Test
    fun `can evolve from lower contract class version to higer one`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.

        val stateAndRef: StateAndRef<MessageState>? = driver(driverParameters) {
            var nodeName = {
                val nodeHandle = startNode(rpcUsers = listOf(user), additionalCordapps = listOf(oldCordapp), regenerateCordappsOnStart = true /*, signCordapp = true*/).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::CreateMessage, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                nodeName
            }()
            var result = {
                val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user), additionalCordapps = listOf(newCordapp), regenerateCordappsOnStart = true/*, signCordapp = true*/).getOrThrow()
                var result: StateAndRef<MessageState>? = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultQuery(MessageState::class.java)
                    page.states.singleOrNull()
                }
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::ConsumeMessage, result!!, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                result = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultQuery(MessageState::class.java)
                    page.states.singleOrNull()
                }
                nodeHandle.stop()
                result
            }()
            result
        }
        assertNotNull(stateAndRef)
        assertEquals(transformetMessage, stateAndRef!!.state.data.message)
    }

    //@Test
    fun `cannot evolve from higher contract class version to lower one`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.

        val stateAndRef: StateAndRef<MessageState>? = driver(driverParameters) {
            var nodeName = {
                val nodeHandle = startNode(rpcUsers = listOf(user), additionalCordapps = listOf(newCordapp), regenerateCordappsOnStart = true/*, signCordapp = true*/).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::CreateMessage, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                nodeName
            }()
            var result = {
                val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user), additionalCordapps = listOf(oldCordapp), regenerateCordappsOnStart = true/*, signCordapp = true*/).getOrThrow()

                var result: StateAndRef<MessageState>? = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultQuery(MessageState::class.java)
                    page.states.singleOrNull()
                }

                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    assertFailsWith(CordaRuntimeException::class) {
                        it.proxy.startFlow(::ConsumeMessage, result!!, defaultNotaryIdentity).returnValue.getOrThrow()
                    }
                }
                result = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultQuery(MessageState::class.java)
                    page.states.singleOrNull()
                }
                nodeHandle.stop()
                result
            }()
            result
        }
        assertNotNull(stateAndRef)
        assertEquals(message, stateAndRef!!.state.data.message)
    }
}

fun isQuasarAgentSpecified(): Boolean {
    val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    return jvmArgs.any { it.startsWith("-javaagent:") && it.contains("quasar") }
}

@StartableByRPC
class CreateMessage(private val message: Message, private val notary: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val messageState = MessageState(message = message, by = ourIdentity)
        val txCommand = Command(DummyMessageContract.Commands.Send(), messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, DUMMY_MESSAGE_CONTRACT_PROGRAM_ID), txCommand)
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        serviceHub.recordTransactions(signedTx)
        return signedTx
    }
}

//TODO merge both flows?
@StartableByRPC
class ConsumeMessage(private val stateRef: StateAndRef<MessageState>, private val notary: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val oldMessageState = stateRef.state.data
        val messageState = MessageState(Message(oldMessageState.message.value + "A"), ourIdentity, stateRef.state.data.linearId)
        val txCommand = Command(DummyMessageContract.Commands.Send(), messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, DUMMY_MESSAGE_CONTRACT_PROGRAM_ID), txCommand, stateRef)
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        serviceHub.recordTransactions(signedTx)
        return signedTx
    }
}

//TODO enrich original MessageContract for new command
const val DUMMY_MESSAGE_CONTRACT_PROGRAM_ID = "net.corda.contracts.DummyMessageContract"

open class DummyMessageContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Send>()
        requireThat {
            // Generic constraints around the IOU transaction.
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<MessageState>().single()
            "Message sender must sign." using (command.signers.containsAll(out.participants.map { it.owningKey }))
            "Message value must not be empty." using (out.message.value.isNotBlank())
        }
    }

    interface Commands : CommandData {
        class Send : Commands
    }
}