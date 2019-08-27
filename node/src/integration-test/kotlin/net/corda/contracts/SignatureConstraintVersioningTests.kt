package net.corda.contracts

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.*
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.User
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

open class SignatureConstraintVersioningTests {

    private val baseUnsigned = cordappWithPackages(MessageState::class.packageName, DummyMessageContract::class.packageName)
    private val base = baseUnsigned.signed()
    val oldUnsignedCordapp = baseUnsigned.copy(versionId = 2)
    val oldCordapp = base.copy(versionId = 2)
    val newCordapp = base.copy(versionId = 3)
    private val newUnsignedCordapp = baseUnsigned.copy(versionId = 3)
    val user = User("mark", "dadada", setOf(startFlow<CreateMessage>(), startFlow<ConsumeMessage>(), invokeRpc("vaultQuery")))
    val message = Message("Hello world!")
    val transformedMessage = Message(message.value + "A")











    @Test
    fun `auto migration from HashConstraint to SignatureConstraint`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
            cordapp = oldUnsignedCordapp,
            newCordapp = newCordapp,
            whiteListedCordapps = emptyMap(),
            systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
            startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is SignatureAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated if 'disableHashConstraints' system property is not set to true`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
            cordapp = oldUnsignedCordapp,
            newCordapp = newCordapp,
            whiteListedCordapps = emptyMap(),
            systemProperties = emptyMap(),
            startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated to SignatureConstraint if new jar is not signed`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
            cordapp = oldUnsignedCordapp,
            newCordapp = newUnsignedCordapp,
            whiteListedCordapps = emptyMap(),
            systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
            startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated to SignatureConstraint if platform version is not 4 or greater`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
            cordapp = oldUnsignedCordapp,
            newCordapp = newCordapp,
            whiteListedCordapps = emptyMap(),
            systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
            startNodesInProcess = false,
            minimumPlatformVersion = 3
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test
    fun `HashConstraint cannot be migrated to SignatureConstraint if a HashConstraint is specified for one state and another uses an AutomaticPlaceholderConstraint`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
            cordapp = oldUnsignedCordapp,
            newCordapp = newCordapp,
            whiteListedCordapps = emptyMap(),
            systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
            startNodesInProcess = false,
            specifyExistingConstraint = true,
            addAnotherAutomaticConstraintState = true
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(2, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs[0].constraint is HashAttachmentConstraint)
        assertTrue(consumingTransaction.outputs[1].constraint is HashAttachmentConstraint)
        assertEquals(
            issuanceTransaction.outputs.single().constraint,
            consumingTransaction.outputs.first().constraint,
            "The constraint from the issuance transaction should be the same constraint used in the consuming transaction"
        )
        assertEquals(
            consumingTransaction.outputs[0].constraint,
            consumingTransaction.outputs[1].constraint,
            "The AutomaticPlaceholderConstraint of the second state should become the same HashConstraint used in other state"
        )
    }

    /**
     * Create an issuance transaction on one version of a cordapp
     * Upgrade the cordapp and create a consuming transaction using it
     */
    fun upgradeCorDappBetweenTransactions(
        cordapp: CustomCordapp,
        newCordapp: CustomCordapp,
        whiteListedCordapps: Map<ContractClassName, List<CustomCordapp>>,
        systemProperties: Map<String, String>,
        startNodesInProcess: Boolean,
        minimumPlatformVersion: Int = 4,
        specifyExistingConstraint: Boolean = false,
        addAnotherAutomaticConstraintState: Boolean = false
    ): Pair<CoreTransaction, CoreTransaction> {

        val whitelistedAttachmentHashes = whiteListedCordapps.mapValues { (_, cordapps) ->
            cordapps.map {
                Files.newInputStream(it.jarFile).readFully().sha256()
            }
        }

        return internalDriver(
            inMemoryDB = false,
            startNodesInProcess = startNodesInProcess,
            networkParameters = testNetworkParameters(
                notaries = emptyList(),
                minimumPlatformVersion = minimumPlatformVersion,
                whitelistedContractImplementations = whitelistedAttachmentHashes
            ),
            systemProperties = systemProperties
        ) {
            // create transaction using first Cordapp
            val (nodeName, baseDirectory, issuanceTransaction) = createIssuanceTransaction(cordapp)
            // delete the first cordapp
            deleteCorDapp(baseDirectory, cordapp)
            // create transaction using the upgraded cordapp resuing   input for transaction
            val consumingTransaction = createConsumingTransaction(
                nodeName,
                newCordapp,
                specifyExistingConstraint,
                addAnotherAutomaticConstraintState
            ).coreTransaction
            issuanceTransaction to consumingTransaction
        }
    }

    private fun DriverDSL.createIssuanceTransaction(cordapp: CustomCordapp): Triple<CordaX500Name, Path, CoreTransaction> {
        val nodeHandle = startNode(
            NodeParameters(
                rpcUsers = listOf(user),
                additionalCordapps = listOf(cordapp)
            )
        ).getOrThrow()
        val nodeName = nodeHandle.nodeInfo.singleIdentity().name
        val tx = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
            it.proxy.startFlow(::CreateMessage, message, defaultNotaryIdentity)
                .returnValue.getOrThrow().coreTransaction
        }
        nodeHandle.stop()
        return Triple(nodeName, nodeHandle.baseDirectory, tx)
    }

    private fun deleteCorDapp(baseDirectory: Path, cordapp: CustomCordapp) {
        val cordappPath =
            baseDirectory.resolve(Paths.get("cordapps")).resolve(cordapp.jarFile.fileName)
        cordappPath.delete()
    }

    private fun DriverDSL.createConsumingTransaction(
        nodeName: CordaX500Name,
        cordapp: CustomCordapp,
        specifyExistingConstraint: Boolean,
        addAnotherAutomaticConstraintState: Boolean
    ): SignedTransaction {
        val nodeHandle = startNode(
            NodeParameters(
                providedName = nodeName,
                rpcUsers = listOf(user),
                additionalCordapps = listOf(cordapp)
            )
        ).getOrThrow()
        val result: StateAndRef<MessageState>? =
            CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                val page = it.proxy.vaultQuery(MessageState::class.java)
                page.states.singleOrNull()
            }
        val transaction =
            CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::ConsumeMessage,
                    result!!,
                    defaultNotaryIdentity,
                    specifyExistingConstraint,
                    addAnotherAutomaticConstraintState
                )
                    .returnValue.getOrThrow()
            }
        nodeHandle.stop()
        return transaction
    }
}

@StartableByRPC
class CreateMessage(private val message: Message, private val notary: Party) :
    FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val messageState = MessageState(message = message, by = ourIdentity)
        val txCommand = Command(
            DummyMessageContract.Commands.Send(),
            messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(
            StateAndContract(
                messageState,
                TEST_MESSAGE_CONTRACT_PROGRAM_ID
            ), txCommand
        )
        txBuilder.toLedgerTransaction(serviceHub).verify()
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        serviceHub.recordTransactions(signedTx)
        return signedTx
    }
}

//TODO merge both flows?
@StartableByRPC
class ConsumeMessage(
    private val stateRef: StateAndRef<MessageState>,
    private val notary: Party,
    private val specifyExistingConstraint: Boolean,
    private val addAnotherAutomaticConstraintState: Boolean
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val oldMessageState = stateRef.state.data
        val messageState = MessageState(
            Message(oldMessageState.message.value + "A"),
            ourIdentity,
            stateRef.state.data.linearId
        )
        val txCommand = Command(
            DummyMessageContract.Commands.Send(),
            messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).apply {
            if (specifyExistingConstraint) {
                addOutputState(messageState, stateRef.state.constraint)
            } else {
                addOutputState(messageState)
            }
            if (addAnotherAutomaticConstraintState) {
                addOutputState(
                    MessageState(
                        Message("Another message"),
                        ourIdentity,
                        UniqueIdentifier()
                    )
                )
            }
            addInputState(stateRef)
            addCommand(txCommand)
        }
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        serviceHub.recordTransactions(signedTx)
        return signedTx
    }
}

@CordaSerializable
data class Message(val value: String)

@BelongsToContract(DummyMessageContract::class)
data class MessageState(
    val message: Message,
    val by: Party,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty> = listOf(by)
}

//TODO enrich original MessageContract for new command
const val TEST_MESSAGE_CONTRACT_PROGRAM_ID = "net.corda.contracts.DummyMessageContract"

open class DummyMessageContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Send>()
        requireThat {
            val out = tx.outputsOfType<MessageState>().first()
            "Message sender must sign." using (command.signers.containsAll(out.participants.map { it.owningKey }))
            "Message value must not be empty." using (out.message.value.isNotBlank())
        }
    }

    interface Commands : CommandData {
        class Send : Commands
    }
}