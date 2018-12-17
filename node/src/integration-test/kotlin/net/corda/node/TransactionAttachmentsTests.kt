package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.stubs.WithText
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.cordappForClasses
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Ignore
import org.junit.Test
import java.security.PublicKey

class TransactionAttachmentsTests {
    // TODO un-ignore after fixing the driver in terms of classpath for external nodes.
    @Ignore("This test always passes because the driver adds the classpath of the running test to out of process nodes.")
    @Test
    fun `state inheriting from type in another CorDapp works in terms of attachments resolution`() {
        val cordapp1 = cordappForClasses(MessageContract.MessageState::class.java, Initiator::class.java, Responder::class.java, MessageContract::class.java, MessageContract.Commands::class.java, MessageContract.Commands.Create::class.java)
        val cordapp2 = cordappForClasses(WithText::class.java)

        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = emptyList())) {
            val nodeA = startNode(NodeParameters(additionalCordapps = setOf(cordapp1, cordapp2))).getOrThrow()
            val nodeB = startNode(NodeParameters(additionalCordapps = setOf(cordapp1, cordapp2))).getOrThrow()

            val message = "Hello hello, is there anybody in there?"
            assertThatCode {
                nodeA.rpc.startFlow(::Initiator, message, nodeB.nodeInfo.singleIdentity(), defaultNotaryIdentity).returnValue.getOrThrow()
            }.doesNotThrowAnyException()
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class Initiator(private val message: String, private val target: Party, private val notary: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val outputState = MessageContract.MessageState(message, listOf(ourIdentity, target))
            val builder = TransactionBuilder(notary).addOutputState(outputState, MessageContract.PROGRAM_ID).addCommand(createMessage(ourIdentity.owningKey))
            val signedTx = serviceHub.signInitialTransaction(builder)
            val session = initiateFlow(target)
            val signedByAllTx = session.sendAndReceive<SignedTransaction>(signedTx).unwrap { it }
            signedByAllTx.verify(serviceHub, checkSufficientSignatures = true)

            subFlow(FinalityFlow(signedByAllTx, session))
        }

        private fun createMessage(vararg signers: PublicKey) = Command<TypeOnlyCommandData>(MessageContract.Commands.Create(), signers.toList())
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val tx = session.receive<SignedTransaction>().unwrap { it }
            tx.verify(serviceHub, checkSufficientSignatures = false)
            val signedTx = serviceHub.addSignature(tx)
            signedTx.verify(serviceHub, checkSufficientSignatures = true)
            session.send(signedTx)
            subFlow(ReceiveFinalityFlow(session, signedTx.id))
        }
    }

    class MessageContract : Contract, WithText {
        class MessageState(override val text: String, override val participants: List<AbstractParty>, override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, WithText

        interface Commands : CommandData {
            class Create : TypeOnlyCommandData(), Commands
        }

        override fun verify(tx: LedgerTransaction) {
            logger.warn("MICHELE - VERIFYING!")
            val command = tx.commandsOfType<MessageContract.Commands.Create>().singleOrNull()
            require(command != null) { "Create command must be there" }
            val state: WithText = tx.outputsOfType<MessageContract.MessageState>().single()
            require(state.text.isNotEmpty()) { "Message text cannot be empty!" }
            logger.warn("MICHELE - VERIFIED!")
        }

        override val text: String = "Contract!"

        companion object {
            const val PROGRAM_ID: ContractClassName = "net.corda.node.TransactionAttachmentsTests\$MessageContract"
            val logger = loggerFor<MessageContract>()
        }
    }
}