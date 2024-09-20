package net.corda.node.internal.artemis

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.whenever
import net.corda.coretesting.internal.rigorousMock
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl
import org.apache.activemq.artemis.core.server.ServerSession
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage
import org.assertj.core.api.Assertions
import org.junit.Test

class UserValidationPluginTest {
    private val plugin = UserValidationPlugin()
    private val coreMessage = ClientMessageImpl(0, false, 0, System.currentTimeMillis(),
            4.toByte(), 1024)
    private val amqpMessage: AMQPMessage
        get() {
            return rigorousMock<AMQPMessage>().also {
                doReturn(coreMessage.validatedUserID).whenever(it).getStringProperty(Message.HDR_VALIDATED_USER)
            }
        }

    private val session = rigorousMock<ServerSession>().also {
        doReturn(ArtemisMessagingComponent.PEER_USER).whenever(it).username
        doReturn(ALICE_NAME.toString()).whenever(it).validatedUser
    }

    @Test(timeout = 300_000)
    fun `accept AMQP message without user`() {
        plugin.beforeSend(session, rigorousMock(), amqpMessage, direct = false, noAutoCreateQueue = false)
    }

    @Test(timeout = 300_000)
    fun `accept AMQP message with user`() {
        coreMessage.validatedUserID = ALICE_NAME.toString()
        plugin.beforeSend(session, rigorousMock(), amqpMessage, direct = false, noAutoCreateQueue = false)
    }

    @Test(timeout = 300_000)
    fun `reject AMQP message with different user`() {
        coreMessage.validatedUserID = BOB_NAME.toString()
        val localAmqpMessage = amqpMessage
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            plugin.beforeSend(session, rigorousMock(), localAmqpMessage, direct = false, noAutoCreateQueue = false)
        }.withMessageContaining(Message.HDR_VALIDATED_USER.toString())
    }

    @Test(timeout = 300_000)
    fun `accept AMQP message with different user on internal session`() {
        val internalSession = rigorousMock<ServerSession>().also {
            doReturn(ArtemisMessagingComponent.NODE_P2P_USER).whenever(it).username
            doReturn(ALICE_NAME.toString()).whenever(it).validatedUser
        }
        coreMessage.validatedUserID = BOB_NAME.toString()
        plugin.beforeSend(internalSession, rigorousMock(), amqpMessage, direct = false, noAutoCreateQueue = false)
    }

    @Test(timeout = 300_000)
    fun `reject core message`() {
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            plugin.beforeSend(session, rigorousMock(), coreMessage, direct = false, noAutoCreateQueue = false)
        }.withMessageContaining("message type")
    }

    @Test(timeout = 300_000)
    fun `reject message with exception`() {
        val messageWithException = rigorousMock<AMQPMessage>().also {
            doThrow(IllegalStateException("My exception")).whenever(it).getStringProperty(any<SimpleString>())
        }
        // Artemis swallows all exceptions except ActiveMQException, so making sure that proper exception is thrown
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            plugin.beforeSend(session, rigorousMock(), messageWithException, direct = false, noAutoCreateQueue = false)
        }.withMessageContaining("Message validation failed")
    }

    @Test(timeout = 300_000)
    fun `reject message with security exception`() {
        val messageWithException = object : AMQPStandardMessage(0, ByteArray(0), null) {
            override fun getApplicationPropertiesMap(createIfAbsent: Boolean): MutableMap<String, Any> {
                throw ActiveMQSecurityException("My security exception")
            }
        }
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            plugin.beforeSend(session, rigorousMock(), messageWithException, direct = false, noAutoCreateQueue = false)
        }.withMessageContaining("My security exception")
    }
}