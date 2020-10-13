package net.corda.node.internal.artemis

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.coretesting.internal.rigorousMock
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager
import org.apache.activemq.artemis.core.server.ServerSession
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPConverter
import org.assertj.core.api.Assertions
import org.junit.Test
import java.lang.IllegalStateException

class UserValidationPluginTest {
    private val plugin = UserValidationPlugin()
    private val coreMessage = ClientMessageImpl(0, false, 0, System.currentTimeMillis(), 4.toByte(), 1024)
    private val session = rigorousMock<ServerSession>().also {
        doReturn(ArtemisMessagingComponent.PEER_USER).whenever(it).username
        doReturn(ALICE_NAME.toString()).whenever(it).validatedUser
    }

    @Test(timeout = 300_000)
    fun `accept AMQP message without user`() {
        plugin.beforeSend(session, rigorousMock(), coreMessage.toAMQPMessage(), direct = false, noAutoCreateQueue = false)
    }

    @Test(timeout = 300_000)
    fun `accept AMQP message with user`() {
        val amqpMsg = coreMessage.toAMQPMessage()
        amqpMsg.putStringProperty("_AMQ_VALIDATED_USER", ALICE_NAME.toString())
        plugin.beforeSend(session, rigorousMock(), amqpMsg, direct = false, noAutoCreateQueue = false)
    }

    @Test(timeout = 300_000)
    fun `reject AMQP message with different user`() {
        val amqpMsg = coreMessage.toAMQPMessage()
        amqpMsg.putStringProperty("_AMQ_VALIDATED_USER", BOB_NAME.toString())
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            plugin.beforeSend(session, rigorousMock(), amqpMsg, direct = false, noAutoCreateQueue = false)
        }.withMessageContaining("_AMQ_VALIDATED_USER")
    }

    @Test(timeout = 300_000)
    fun `accept AMQP message with different user on internal session`() {
        val internalSession = rigorousMock<ServerSession>().also {
            doReturn(ArtemisMessagingComponent.NODE_P2P_USER).whenever(it).username
            doReturn(ALICE_NAME.toString()).whenever(it).validatedUser
        }
        val msg = coreMessage.toAMQPMessage()
        msg.putStringProperty("_AMQ_VALIDATED_USER", BOB_NAME.toString())
        plugin.beforeSend(internalSession, rigorousMock(), msg, direct = false, noAutoCreateQueue = false)
    }

    @Test(timeout = 300_000)
    fun `reject core message`() {
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            plugin.beforeSend(session, rigorousMock(), coreMessage, direct = false, noAutoCreateQueue = false)
        }.withMessageContaining("message type")
    }

    @Test(timeout = 300_000)
    fun `reject message with exception`() {
        val messageWithException = mock<AMQPMessage>()
        whenever(messageWithException.getStringProperty(any<SimpleString>())).thenThrow(IllegalStateException("My exception"))
        // Artemis swallows all exceptions except ActiveMQException, so making sure that proper exception is thrown
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            plugin.beforeSend(session, rigorousMock(), messageWithException, direct = false, noAutoCreateQueue = false)
        }.withMessageContaining("Message validation failed")
    }

    private fun ClientMessageImpl.toAMQPMessage(): AMQPMessage = AMQPConverter.getInstance().fromCore(this, NullStorageManager())
}