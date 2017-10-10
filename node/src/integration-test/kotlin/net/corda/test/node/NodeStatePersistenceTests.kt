package net.corda.test.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.FlowPermissions
import net.corda.nodeapi.User
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.chooseIdentity
import net.corda.testing.driver.driver
import org.junit.Test
import java.lang.management.ManagementFactory
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
import kotlin.test.assertEquals

class NodeStatePersistenceTests {

    @Test
    fun `persistent state survives node restart`() {
        val user = User("mark", "dadada", setOf(FlowPermissions.startFlowPermission<SendMessageFlow>()))
        val message = Message("Hello world!")
        driver(isDebug = true, startNodesInProcess = isQuasarAgentSpecified()) {
            startNotaryNode(DUMMY_NOTARY.name, validating = false).getOrThrow()
            var nodeHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
            val nodeName = nodeHandle.nodeInfo.chooseIdentity().name
            nodeHandle.rpcClientToNode().start(user.username, user.password).use {
                it.proxy.startFlow(::SendMessageFlow, message).returnValue.getOrThrow()
            }
            nodeHandle.stop().getOrThrow()

            nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user)).getOrThrow()
            nodeHandle.rpcClientToNode().start(user.username, user.password).use {
                val page = it.proxy.vaultQuery(MessageState::class.java)
                val retrievedMessage = page.states.singleOrNull()?.state?.data?.message
                assertEquals(message, retrievedMessage)
            }
        }
    }
}

fun isQuasarAgentSpecified(): Boolean {
    val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    return jvmArgs.any { it.startsWith("-javaagent:") && it.endsWith("quasar.jar") }
}

@CordaSerializable
data class Message(val value: String)

data class MessageState(val message: Message, val by: Party, override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
    override val participants: List<AbstractParty> = listOf(by)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is MessageSchemaV1 -> MessageSchemaV1.PersistentMessage(
                    by = by.name.toString(),
                    value = message.value
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(MessageSchemaV1)
}

object MessageSchema
object MessageSchemaV1 : MappedSchema(
        schemaFamily = MessageSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentMessage::class.java)) {

    @Entity
    @Table(name = "messages")
    class PersistentMessage(
            @Column(name = "by")
            var by: String,

            @Column(name = "value")
            var value: String
    ) : PersistentState()
}

val MESSAGE_CONTRACT_PROGRAM_ID = "net.corda.test.node.MessageContract"

open class MessageContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Send>()
        requireThat {
            // Generic constraints around the IOU transaction.
            "No inputs should be consumed when sending a message." using (tx.inputs.isEmpty())
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

@StartableByRPC
class SendMessageFlow(private val message: Message) : FlowLogic<SignedTransaction>() {
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
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

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