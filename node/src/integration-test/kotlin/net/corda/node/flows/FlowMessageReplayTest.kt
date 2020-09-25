package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.MessageRecipients
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import net.corda.node.services.statemachine.MessageType.DATA_MESSAGE
import net.corda.node.services.statemachine.MessageType.SESSION_CONFIRM
import net.corda.node.services.statemachine.MessageType.SESSION_INIT
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MessagingServiceSpy
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.getMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowMessageReplayTest {

    private lateinit var mockNetwork: InternalMockNetwork
    private lateinit var partyA: TestStartedNode
    private lateinit var partyB: TestStartedNode

    companion object {
        private const val NUMBER_OF_MESSAGES = 100
    }

    @Before
    fun setup() {
        mockNetwork = InternalMockNetwork(
                cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList(),
                threadPerNode = true,
                networkSendManuallyPumped = false
        )
        partyA = mockNetwork.createNode(InternalMockNodeParameters(legalName = CordaX500Name("PartyA", "Berlin", "DE")))
        partyB = mockNetwork.createNode(InternalMockNodeParameters(legalName = CordaX500Name("PartyB", "Berlin", "DE")))
        mockNetwork.startNodes()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
        StaffedFlowHospital.onFlowKeptForOvernightObservation.clear()
    }

    @Test(timeout=300_000)
    fun `messages that are being replayed by the messaging layer are successfully deduplicated by the state machine`() {
        var messagesReceived = emptyList<InMemoryMessagingNetwork.MessageTransfer>()
        mockNetwork.messagingNetwork.receivedMessages.subscribe { messagesReceived = messagesReceived + it }
        partyA.setMessagingServiceSpy(DelayedReplayMessagingServiceSpy())
        partyB.setMessagingServiceSpy(DelayedReplayMessagingServiceSpy())

        partyA.services.startFlow(InitiatorFlow(partyB.info.legalIdentities.first(), NUMBER_OF_MESSAGES), InvocationContext.shell()).toCompletableFuture().get().resultFuture.get()

        val dataMessagesFromA = filterMessages(messagesReceived, partyA, DATA_MESSAGE).size
        val sessionConfirmationFromB = filterMessages(messagesReceived, partyB, SESSION_CONFIRM).size
        // all messages are sent twice except the first one
        assertThat(dataMessagesFromA).isEqualTo(2*NUMBER_OF_MESSAGES-1)
        assertThat(sessionConfirmationFromB).isEqualTo(2)
    }

    @Test(timeout=300_000)
    fun `messages that are being reordered by the network are being processed successfully in order by the state machine`() {
        val messagesReceived = mutableListOf<InMemoryMessagingNetwork.MessageTransfer>()
        mockNetwork.messagingNetwork.receivedMessages.subscribe { messagesReceived.add(it) }
        partyA.setMessagingServiceSpy(ReorderingMessagingServiceSpy())

        partyA.services.startFlow(InitiatorFlow(partyB.info.legalIdentities.first(), NUMBER_OF_MESSAGES), InvocationContext.shell()).toCompletableFuture().get().resultFuture.get()
    }

    private fun filterMessages(messages: List<InMemoryMessagingNetwork.MessageTransfer>, from: TestStartedNode, messageType: net.corda.node.services.statemachine.MessageType): List<InMemoryMessagingNetwork.MessageTransfer> {
        return messages.filter {
            it.sender.name == from.info.legalIdentities.first().name &&
            it.getMessage().uniqueMessageId.messageType == messageType
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class InitiatorFlow(private val other: Party, private val numberOfMessages: Int) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val session = initiateFlow(other)
            session.send(Message(MessageType.INITIAL_SESSION_MESSAGE, numberOfMessages))

            for(step in 1..numberOfMessages) {
                session.send(Message(MessageType.REGULAR_DATA_MESSAGE, step))
                logger.info("Sending message $step")
            }

            val closingMessage = session.receive<Message>().unwrap { it }
            assertThat(closingMessage.type).isEqualTo(MessageType.REGULAR_DATA_MESSAGE)
            assertThat(closingMessage.payload).isEqualTo(1)
        }

    }

    @InitiatedBy(InitiatorFlow::class)
    class InitiatedFlow(val session: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val initialMessage = session.receive<Message>().unwrap { it }
            assertThat(initialMessage.type).isEqualTo(MessageType.INITIAL_SESSION_MESSAGE)
            val numberOfMessages = initialMessage.payload

            for(step in 1..numberOfMessages) {
                val dataMessage = session.receive<Message>().unwrap { it }
                assertThat(dataMessage.type).isEqualTo(MessageType.REGULAR_DATA_MESSAGE)
                assertThat(dataMessage.payload).isEqualTo(step)
                logger.info("Received message $step")
            }

            session.send(Message(MessageType.REGULAR_DATA_MESSAGE, 1))
        }

    }


    @CordaSerializable
    data class Message(val type: MessageType, val payload: Int)

    @CordaSerializable
    enum class MessageType {
        INITIAL_SESSION_MESSAGE,
        REGULAR_DATA_MESSAGE
    }

    class DelayedReplayMessagingServiceSpy: MessagingServiceSpy() {
        private var lastMessage: Triple<net.corda.node.services.messaging.Message, MessageRecipients, Any>? = null

        override fun send(message: net.corda.node.services.messaging.Message, target: MessageRecipients, sequenceKey: Any) {
            if (message.uniqueMessageId.messageType != SESSION_INIT) {
                if (lastMessage == null) {
                    messagingService.send(message, target, sequenceKey)
                    lastMessage = Triple(message, target, sequenceKey)
                } else {
                    messagingService.send(message, target, sequenceKey)
                    // replay old message
                    messagingService.send(lastMessage!!.first, lastMessage!!.second, lastMessage!!.third)
                    lastMessage = Triple(message, target, sequenceKey)
                }
            } else {
                messagingService.send(message, target, sequenceKey)
            }
        }
    }

    class ReorderingMessagingServiceSpy: MessagingServiceSpy() {
        private val buffer: MutableList<Triple<net.corda.node.services.messaging.Message, MessageRecipients, Any>> = mutableListOf()

        override fun send(message: net.corda.node.services.messaging.Message, target: MessageRecipients, sequenceKey: Any) {
            if (message.uniqueMessageId.messageType == DATA_MESSAGE) {
                buffer.add(Triple(message, target, sequenceKey))

                if (buffer.size == 5) {
                    buffer.shuffle()
                    buffer.forEach { msg ->
                        messagingService.send(msg.first, msg.second, msg.third)
                    }
                    buffer.clear()
                }
            } else {
                messagingService.send(message, target, sequenceKey)
            }
        }
    }

}