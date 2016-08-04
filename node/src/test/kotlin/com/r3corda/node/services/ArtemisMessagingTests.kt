package com.r3corda.node.services

import com.google.common.net.HostAndPort
import com.r3corda.core.messaging.Message
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.testing.freeLocalHostAndPort
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingClient
import com.r3corda.node.services.messaging.ArtemisMessagingServer
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArtemisMessagingTests {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    val hostAndPort = freeLocalHostAndPort()
    val topic = "platform.self"
    val config = object : NodeConfiguration {
        override val myLegalName: String = "me"
        override val exportJMXto: String = ""
        override val nearestCity: String = "London"
        override val keyStorePassword: String = "testpass"
        override val trustStorePassword: String = "trustpass"
    }

    var messagingClient: ArtemisMessagingClient? = null
    var messagingServer: ArtemisMessagingServer? = null

    @After
    fun cleanUp() {
        messagingClient?.stop()
        messagingServer?.stop()
    }

    @Test
    fun `server starting with the port already bound should throw`() {
        ServerSocket(hostAndPort.port).use {
            val messagingServer = createMessagingServer()
            assertThatThrownBy { messagingServer.start() }
        }
    }

    @Test
    fun `client should connect to remote server`() {
        val remoteServerAddress = freeLocalHostAndPort()

        createMessagingServer(remoteServerAddress).start()
        createMessagingClient(server = remoteServerAddress).start()
    }

    @Test
    fun `client should throw if remote server not found`() {
        val serverAddress = freeLocalHostAndPort()
        val invalidServerAddress = freeLocalHostAndPort()

        createMessagingServer(serverAddress).start()

        val messagingClient = createMessagingClient(server = invalidServerAddress)
        assertThatThrownBy { messagingClient.start() }
    }

    @Test
    fun `client should connect to local server`() {
        createMessagingServer().start()
        createMessagingClient().start()
    }

    @Test
    fun `client should be able to send message to itself`() {
        val receivedMessages = LinkedBlockingQueue<Message>()

        createMessagingServer().start()

        val messagingClient = createMessagingClient()
        messagingClient.start()

        messagingClient.addMessageHandler(topic) { message, r ->
            receivedMessages.add(message)
        }

        val message = messagingClient.createMessage(topic, DEFAULT_SESSION_ID, "first msg".toByteArray())
        messagingClient.send(message, messagingClient.myAddress)

        val actual = receivedMessages.poll(2, SECONDS)
        assertNotNull(actual)
        assertEquals("first msg", String(actual.data))
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    private fun createMessagingClient(server: HostAndPort = hostAndPort,
                                      local: HostAndPort = hostAndPort): ArtemisMessagingClient {
        return ArtemisMessagingClient(temporaryFolder.newFolder().toPath(), config, server, local).apply {
            configureWithDevSSLCertificate()
            messagingClient = this
        }
    }

    private fun createMessagingServer(local: HostAndPort = hostAndPort): ArtemisMessagingServer {
        return ArtemisMessagingServer(temporaryFolder.newFolder().toPath(), config, local).apply {
            configureWithDevSSLCertificate()
            messagingServer = this
        }
    }
}