package com.r3corda.node.services

import com.r3corda.core.messaging.Message
import com.r3corda.core.testing.freeLocalHostAndPort
import com.r3corda.node.services.messaging.ArtemisMessagingService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

class ArtemisMessagingServiceTests {

    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    val hostAndPort = freeLocalHostAndPort()
    val topic = "platform.self"

    var messagingNetwork: ArtemisMessagingService? = null

    @After
    fun cleanUp() {
        messagingNetwork?.stop()
    }

    @Test
    fun `starting with the port already bound`() {
        ServerSocket(hostAndPort.port).use {
            val messagingNetwork = createMessagingService()
            assertThatThrownBy { messagingNetwork.start() }
        }
    }

    @Test
    fun `sending message to self`() {
        val receivedMessages = LinkedBlockingQueue<Message>()

        val messagingNetwork = createMessagingService()
        messagingNetwork.start()

        messagingNetwork.addMessageHandler(topic) { message, r ->
            receivedMessages.add(message)
        }

        val message = messagingNetwork.createMessage(topic, "first msg".toByteArray())
        messagingNetwork.send(message, messagingNetwork.myAddress)

        assertThat(String(receivedMessages.poll(2, SECONDS).data)).isEqualTo("first msg")
        assertThat(receivedMessages.poll(200, MILLISECONDS)).isNull()
    }

    private fun createMessagingService(): ArtemisMessagingService {
        return ArtemisMessagingService(temporaryFolder.newFolder().toPath(), hostAndPort).apply {
            configureWithDevSSLCertificate()
            messagingNetwork = this
        }
    }

}