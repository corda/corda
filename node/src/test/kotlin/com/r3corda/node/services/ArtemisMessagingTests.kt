package com.r3corda.node.services

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.messaging.Message
import com.r3corda.core.messaging.createMessage
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingServer
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.node.services.network.InMemoryNetworkMapCache
import com.r3corda.node.utilities.AffinityExecutor
import com.r3corda.testing.freeLocalHostAndPort
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtemisMessagingTests {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    val hostAndPort = freeLocalHostAndPort()
    val topic = "platform.self"
    val identity = generateKeyPair()

    lateinit var config: NodeConfiguration

    var messagingClient: NodeMessagingClient? = null
    var messagingServer: ArtemisMessagingServer? = null

    val networkMapCache = InMemoryNetworkMapCache()

    @Before
    fun setUp() {
        // TODO: create a base class that provides a default implementation
        config = object : NodeConfiguration {
            override val basedir: Path = temporaryFolder.newFolder().toPath()
            override val myLegalName: String = "me"
            override val nearestCity: String = "London"
            override val emailAddress: String = ""
            override val devMode: Boolean = true
            override val exportJMXto: String = ""
            override val keyStorePassword: String = "testpass"
            override val trustStorePassword: String = "trustpass"
        }
    }

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

        messagingClient = createMessagingClient(server = invalidServerAddress)
        assertThatThrownBy { messagingClient!!.start() }
        messagingClient = null
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
        thread { messagingClient.run() }

        messagingClient.addMessageHandler(topic) { message, r ->
            receivedMessages.add(message)
        }

        val message = messagingClient.createMessage(topic, DEFAULT_SESSION_ID, "first msg".toByteArray())
        messagingClient.send(message, messagingClient.myAddress)

        val actual: Message = receivedMessages.take()
        assertEquals("first msg", String(actual.data))
        assertNull(receivedMessages.poll(200, MILLISECONDS))
    }

    private fun createMessagingClient(server: HostAndPort = hostAndPort): NodeMessagingClient {
        return NodeMessagingClient(config, server, identity.public, AffinityExecutor.SAME_THREAD, false).apply {
            configureWithDevSSLCertificate()
            messagingClient = this
        }
    }

    private fun createMessagingServer(local: HostAndPort = hostAndPort): ArtemisMessagingServer {
        return ArtemisMessagingServer(config, local, networkMapCache).apply {
            configureWithDevSSLCertificate()
            messagingServer = this
        }
    }
}