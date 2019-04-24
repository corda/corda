package net.corda.contracts

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.*
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testMessage.Message
import net.corda.testMessage.MessageState
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.User
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SignatureConstraintVersioningTests {

    private val baseUnsinded = cordappWithPackages(MessageState::class.packageName, DummyMessageContract::class.packageName)
    private val base = baseUnsinded.signed()
    private val oldUnsigedCordapp = baseUnsinded.copy(versionId = 2)
//    private val base = cordappWithPackages(MessageState::class.packageName, DummyMessageContract::class.packageName).signed()
    private val oldCordapp = base.copy(versionId = 2)
    private val newCordapp = base.copy(versionId = 3)
    private val user = User("mark", "dadada", setOf(startFlow<CreateMessage>(), startFlow<ConsumeMessage>(), invokeRpc("vaultQuery")))
    private val message = Message("Hello world!")
    private val transformetMessage = Message(message.value + "A")

    @Test
    fun `can evolve from lower contract class version to higher one`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.

        val stateAndRef: StateAndRef<MessageState>? = internalDriver(
            inMemoryDB = false,
            startNodesInProcess = isQuasarAgentSpecified(),
            networkParameters = testNetworkParameters(notaries = emptyList(), minimumPlatformVersion = 4)
        ) {
            val nodeName = {
                val nodeHandle = startNode(NodeParameters(rpcUsers = listOf(user), additionalCordapps = listOf(oldCordapp))).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::CreateMessage, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                nodeName
            }()
            val result = {
                (baseDirectory(nodeName) / "cordapps").deleteRecursively()
                val nodeHandle = startNode(
                    NodeParameters(
                        providedName = nodeName,
                        rpcUsers = listOf(user),
                        additionalCordapps = listOf(newCordapp)
                    )
                ).getOrThrow()
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

    @Test
    fun `auto migration from WhitelistConstraint to SignatureConstraint`() {
        val stateAndRef =
            `use states to create transaction with newer cordapp`(oldUnsigedCordapp, newCordapp, listOf(oldUnsigedCordapp, newCordapp))
        assertNotNull(stateAndRef)
        assertEquals(transformetMessage, stateAndRef!!.state.data.message)
    }

    //TODO the test actually doesn't check that signature constraint was used for the second transaction
    @Test
    fun `auto migration from WhitelistConstraint to SignatureConstraint fail for not whitelisted signed JAR`() {
        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            `use states to create transaction with newer cordapp`(oldUnsigedCordapp, newCordapp, emptyList())
        }.withMessageContaining("Selected output constraint: $WhitelistedByZoneAttachmentConstraint not satisfying")
    }

    private fun `use states to create transaction with newer cordapp`(
        cordapp: CustomCordapp,
        newCordapp: CustomCordapp,
        whiteListedCordapps: List<CustomCordapp>
    ): StateAndRef<MessageState>? {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.

        val attachmentHashes = whiteListedCordapps.map { Files.newInputStream(it.jarFile).readFully().sha256() }

        return internalDriver(
            inMemoryDB = false,
            startNodesInProcess = isQuasarAgentSpecified(),
            networkParameters = testNetworkParameters(
                notaries = emptyList(),
                minimumPlatformVersion = 4, whitelistedContractImplementations = mapOf(TEST_MESSAGE_CONTRACT_PROGRAM_ID to attachmentHashes)
            )
        ) {
            // create transaction using first Cordapp
            var (nodeName, baseDirectory) = {
                val nodeHandle = startNode(NodeParameters(rpcUsers = listOf(user), additionalCordapps = listOf(cordapp))).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::CreateMessage, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                Pair(nodeName, nodeHandle.baseDirectory)
            }()

            // delete the first cordapp
            val cordappPath = baseDirectory.resolve(Paths.get("cordapps")).resolve(cordapp.jarFile.fileName)
            cordappPath.delete()

            // create transaction using the upgraded cordapp resuing   input for transaction
            var result = {
                val nodeHandle = startNode(
                    NodeParameters(
                        providedName = nodeName,
                        rpcUsers = listOf(user),
                        additionalCordapps = listOf(newCordapp)
                    )
                ).getOrThrow()
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
    }
}

@StartableByRPC
class CreateMessage(private val message: Message, private val notary: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val messageState = MessageState(message = message, by = ourIdentity)
        val txCommand = Command(DummyMessageContract.Commands.Send(), messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, TEST_MESSAGE_CONTRACT_PROGRAM_ID), txCommand)
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
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, TEST_MESSAGE_CONTRACT_PROGRAM_ID), txCommand, stateRef)
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        serviceHub.recordTransactions(signedTx)
        return signedTx
    }
}

//TODO enrich original MessageContract for new command
const val TEST_MESSAGE_CONTRACT_PROGRAM_ID = "net.corda.contracts.DummyMessageContract"

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