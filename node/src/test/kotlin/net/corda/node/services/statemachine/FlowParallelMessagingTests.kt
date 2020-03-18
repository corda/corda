package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.Destination
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

class FlowParallelMessagingTests {

    companion object {

        private lateinit var mockNet: InternalMockNetwork
        private lateinit var senderNode: TestStartedNode
        private lateinit var recipientNode1: TestStartedNode
        private lateinit var recipientNode2: TestStartedNode
        private lateinit var notaryIdentity: Party
        private lateinit var senderParty: Party
        private lateinit var recipientParty1: Party
        private lateinit var recipientParty2: Party

        @BeforeClass
        @JvmStatic
        fun setup() {
            mockNet = InternalMockNetwork(
                    cordappsForAllNodes = listOf(enclosedCordapp())
            )

            senderNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME.copy(organisation = "SenderNode")))
            recipientNode1 = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME.copy(organisation = "RecipientNode1")))
            recipientNode2 = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME.copy(organisation = "RecipientNode2")))

            notaryIdentity = mockNet.defaultNotaryIdentity
            senderParty = senderNode.info.singleIdentity()
            recipientParty1 = recipientNode1.info.singleIdentity()
            recipientParty2 = recipientNode2.info.singleIdentity()
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            mockNet.stopNodes()
        }
    }


    @Test(timeout=300_000)
    fun `messages can be exchanged in parallel using sendAll & receiveAll between multiple parties successfully`() {
        val messages = mapOf(
                recipientParty1 to MessageType.REPLY,
                recipientParty2 to MessageType.REPLY
        )
        val flow = senderNode.services.startFlow(SenderFlow(messages))

        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()

        assertEquals("ok", result)
    }

    @Test(timeout=300_000)
    fun `flow exceptions from counterparties during receiveAll are handled properly`() {
        val messages = mapOf(
                recipientParty1 to MessageType.REPLY,
                recipientParty2 to MessageType.GRACEFUL_FAILURE
        )
        val flow = senderNode.services.startFlow(SenderFlow(messages))

        mockNet.runNetwork()
        assertThatThrownBy{ flow.resultFuture.getOrThrow() }
                .isInstanceOf(FlowException::class.java)
                .hasMessage("graceful failure")
    }

    @Test(timeout=300_000)
    fun `runtime exceptions from counterparties during receiveAll are handled properly`() {
        val messages = mapOf(
                recipientParty1 to MessageType.REPLY,
                recipientParty2 to MessageType.CRASH
        )
        val flow = senderNode.services.startFlow(SenderFlow(messages))

        mockNet.runNetwork()
        assertThatThrownBy{ flow.resultFuture.getOrThrow() }
                .isInstanceOf(UnexpectedFlowEndException::class.java)
    }

    @Test(timeout=300_000)
    fun `initial session messages and existing session messages can be sent together using sendAll`() {
        val flow = senderNode.services.startFlow(StagedSenderFlow(listOf(recipientParty1, recipientParty2)))

        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()

        assertEquals("ok", result)
    }

    @Test(timeout=300_000)
    fun `messages can be exchanged successfully even between anonymous parties`() {
        val senderAnonymousParty = senderNode.createConfidentialIdentity(senderParty)
        val firstRecipientAnonymousParty = recipientNode1.createConfidentialIdentity(recipientParty1)
        senderNode.verifyAndRegister(firstRecipientAnonymousParty)
        val secondRecipientAnonymousParty = recipientNode2.createConfidentialIdentity(recipientParty2)
        senderNode.verifyAndRegister(secondRecipientAnonymousParty)

        val messages = mapOf(
                senderAnonymousParty.party.anonymise() to MessageType.REPLY,
                firstRecipientAnonymousParty.party.anonymise() to MessageType.REPLY,
                secondRecipientAnonymousParty.party.anonymise() to MessageType.REPLY
        )

        val flow = senderNode.services.startFlow(SenderFlow(messages))

        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()

        assertEquals("ok", result)
    }

    @Test(timeout=300_000)
    fun `a flow cannot invoke receiveAll with duplicate sessions`() {
        val flow = senderNode.services.startFlow(InvalidReceiveFlow(listOf(recipientParty1), String::class.java))

        mockNet.runNetwork()

        assertThatThrownBy{ flow.resultFuture.getOrThrow() }
                .isInstanceOf(java.lang.IllegalArgumentException::class.java)
                .hasMessage("A flow session can only appear once as argument.")
    }

    fun TestStartedNode.createConfidentialIdentity(party: Party) =
            services.keyManagementService.freshKeyAndCert(services.myInfo.legalIdentitiesAndCerts.single { it.name == party.name }, false)

    fun TestStartedNode.verifyAndRegister(identity: PartyAndCertificate) =
            services.identityService.verifyAndRegisterIdentity(identity)

    @StartableByRPC
    @InitiatingFlow
    class SenderFlow(private val parties: Map<out Destination, MessageType>): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val messagesPerSession = parties.toList().map { (party, messageType) ->
                val session = initiateFlow(party)
                Pair(session, messageType)
            }.toMap()

            sendAllMap(messagesPerSession)
            val messages = receiveAll(String::class.java, messagesPerSession.keys.toList())

            messages.map { it.unwrap { payload -> assertEquals("pong", payload) } }

            return "ok"
        }
    }

    @Suppress("TooGenericExceptionThrown")
    @InitiatedBy(SenderFlow::class)
    class RecipientFlow(private val otherPartySession: FlowSession): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val msg = otherPartySession.receive<MessageType>().unwrap { it }
            when (msg) {
                MessageType.REPLY -> otherPartySession.send("pong")
                MessageType.GRACEFUL_FAILURE -> throw FlowException("graceful failure")
                MessageType.CRASH -> throw RuntimeException("crash")
            }

            return "ok"
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class StagedSenderFlow(private val parties: List<Destination>): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            if (parties.size < 2) {
                throw IllegalArgumentException("at least two parties required for staged execution")
            }

            val sessions = parties.map { initiateFlow(it) }.toSet()

            sessions.first().send(StagedMessageType.INITIAL_RECIPIENT)
            sessions.first().receive<String>().unwrap{ payload -> assertEquals("pong", payload) }

            sendAll(StagedMessageType.REGULAR_RECIPIENT, sessions)
            val messages = receiveAll(String::class.java, sessions.toList())

            messages.map { it.unwrap { payload -> assertEquals("pong", payload) } }

            return "ok"
        }
    }

    @InitiatedBy(StagedSenderFlow::class)
    class StagedRecipientFlow(private val otherPartySession: FlowSession): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val msg = otherPartySession.receive<StagedMessageType>().unwrap { it }
            when (msg) {
                StagedMessageType.INITIAL_RECIPIENT -> {
                    otherPartySession.send("pong")
                    otherPartySession.receive<StagedMessageType>().unwrap { payload -> assertEquals(StagedMessageType.REGULAR_RECIPIENT, payload) }
                    otherPartySession.send("pong")
                }
                StagedMessageType.REGULAR_RECIPIENT -> otherPartySession.send("pong")
            }

            return "ok"
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class InvalidReceiveFlow<R: Any>(private val parties: List<Party>, private val payloadType: Class<R>): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val sessions = parties.flatMap { party ->
                val session = initiateFlow(party)
                listOf(session, session)
            }
            receiveAll(payloadType, sessions)
            return "ok"
        }
    }

    @CordaSerializable
    enum class MessageType {
        REPLY,
        GRACEFUL_FAILURE,
        CRASH
    }

    @CordaSerializable
    enum class StagedMessageType {
        INITIAL_RECIPIENT,
        REGULAR_RECIPIENT
    }

}