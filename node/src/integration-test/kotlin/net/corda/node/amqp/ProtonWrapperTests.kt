package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisTcpTransport
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.installDevNodeCaCertPath
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.*
import net.corda.nodeapi.internal.registerDevP2pCertificates
import net.corda.testing.core.*
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.apache.activemq.artemis.api.core.RoutingType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@RunWith(Parameterized::class)
class ProtonWrapperTests(val sslSetup: SslSetup) {
    companion object {
        data class SslSetup(val clientNative: Boolean, val serverNative: Boolean) {
            override fun toString(): String = "Client: ${if (clientNative) "openSsl" else "javaSsl"} Server: ${if (serverNative) "openSsl" else "javaSsl"} "
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<SslSetup> = listOf(
                SslSetup(false, false),
                SslSetup(true, false),
                SslSetup(false, true),
                SslSetup(true, true)
        )
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val portAllocation = incrementalPortAllocation() // use 15000 to move us out of harms way
    private val serverPort = portAllocation.nextPort()
    private val serverPort2 = portAllocation.nextPort()
    private val artemisPort = portAllocation.nextPort()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `Simple AMPQ Client to Server`() {
        val amqpServer = createServer(serverPort)
        amqpServer.use {
            amqpServer.start()
            val receiveSubs = amqpServer.onReceive.subscribe {
                assertEquals(BOB_NAME.toString(), it.sourceLegalName)
                assertEquals(P2P_PREFIX + "Test", it.topic)
                assertEquals("Test", String(it.payload))
                it.complete(true)
            }
            val amqpClient = createClient()
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
                assertEquals(BOB_NAME, CordaX500Name.build(serverConnect.remoteCert!!.subjectX500Principal))
                val clientConnect = clientConnected.get()
                assertEquals(true, clientConnect.connected)
                assertEquals(ALICE_NAME, CordaX500Name.build(clientConnect.remoteCert!!.subjectX500Principal))
                val msg = amqpClient.createMessage("Test".toByteArray(),
                        P2P_PREFIX + "Test",
                        ALICE_NAME.toString(),
                        emptyMap())
                amqpClient.write(msg)
                assertEquals(MessageStatus.Acknowledged, msg.onComplete.get())
                receiveSubs.unsubscribe()
            }
        }
    }

    @Test
    fun `AMPQ Client fails to connect when crl soft fail check is disabled`() {
        val amqpServer = createServer(serverPort, maxMessageSize = MAX_MESSAGE_SIZE, crlCheckSoftFail = false)
        amqpServer.use {
            amqpServer.start()
            val amqpClient = createClient()
            amqpClient.use {
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val clientConnect = clientConnected.get()
                assertEquals(false, clientConnect.connected)
            }
        }
    }

    @Test
    fun `AMPQ Client refuses to connect to unexpected server`() {
        val amqpServer = createServer(serverPort, CordaX500Name("Rogue 1", "London", "GB"))
        amqpServer.use {
            amqpServer.start()
            val amqpClient = createClient()
            amqpClient.use {
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val clientConnect = clientConnected.get()
                assertEquals(false, clientConnect.connected)
            }
        }
    }

    private fun MutualSslConfiguration.createTrustStore(rootCert: X509Certificate) {

        trustStore.get(true)[X509Utilities.CORDA_ROOT_CA] = rootCert
    }

    @Test
    fun `Test AMQP Client with invalid root certificate`() {
        val certificatesDirectory = temporaryFolder.root.toPath()
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory, "serverstorepass")
        val sslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, keyStorePassword = "serverstorepass")

        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        // Generate server cert and private key and populate another keystore suitable for SSL
        signingCertificateStore.get(true).also { it.installDevNodeCaCertPath(ALICE_NAME, rootCa.certificate, intermediateCa) }
        sslConfig.keyStore.get(true).also { it.registerDevP2pCertificates(ALICE_NAME, rootCa.certificate, intermediateCa) }
        sslConfig.createTrustStore(rootCa.certificate)

        val keyStore = sslConfig.keyStore.get()
        val trustStore = sslConfig.trustStore.get()

        val context = SSLContext.getInstance("TLS")
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore)
        val keyManagers = keyManagerFactory.keyManagers
        val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustMgrFactory.init(trustStore)
        val trustManagers = trustMgrFactory.trustManagers
        context.init(keyManagers, trustManagers, newSecureRandom())

        val serverSocketFactory = context.serverSocketFactory

        val serverSocket = serverSocketFactory.createServerSocket(serverPort) as SSLServerSocket
        val serverParams = SSLParameters(ArtemisTcpTransport.CIPHER_SUITES.toTypedArray(),
                arrayOf("TLSv1.2"))
        serverParams.wantClientAuth = true
        serverParams.needClientAuth = true
        serverParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        serverSocket.sslParameters = serverParams
        serverSocket.useClientMode = false

        val lock = Object()
        var done = false
        var handshakeError = false

        val serverThread = thread {
            try {
                val sslServerSocket = serverSocket.accept() as SSLSocket
                sslServerSocket.addHandshakeCompletedListener {
                    done = true
                }
                sslServerSocket.startHandshake()
                synchronized(lock) {
                    while (!done) {
                        lock.wait(1000)
                    }
                }
                sslServerSocket.close()
            } catch (ex: SSLHandshakeException) {
                handshakeError = true
            }
        }

        val amqpClient = createClient()
        amqpClient.use {
            val clientConnected = amqpClient.onConnection.toFuture()
            amqpClient.start()
            val clientConnect = clientConnected.get()
            assertEquals(false, clientConnect.connected)
            synchronized(lock) {
                done = true
                lock.notifyAll()
            }
        }
        serverThread.join(1000)
        assertTrue(handshakeError)
        serverSocket.close()
        assertTrue(done)
    }


    @Test
    fun `Client Failover for multiple IP`() {
        val amqpServer = createServer(serverPort)
        val amqpServer2 = createServer(serverPort2)
        val amqpClient = createClient()
        try {
            // The filter here is to prevent rogue RPC clients from messing us up
            val serverConnected = amqpServer.onConnection.filter { it.remoteCert != null }.toFuture()
            val serverConnected2 = amqpServer2.onConnection.filter { it.remoteCert != null }.toFuture()
            val clientConnected = amqpClient.onConnection.filter { it.remoteCert != null }.toBlocking().iterator
            amqpServer.start()
            amqpClient.start()
            val serverConn1 = serverConnected.get()
            assertEquals(true, serverConn1.connected)
            assertEquals(BOB_NAME, CordaX500Name.build(serverConn1.remoteCert!!.subjectX500Principal))
            val connState1 = clientConnected.next()
            assertEquals(true, connState1.connected)
            assertEquals(ALICE_NAME, CordaX500Name.build(connState1.remoteCert!!.subjectX500Principal))
            assertEquals(serverPort, connState1.remoteAddress.port)

            // Fail over
            amqpServer2.start()
            amqpServer.stop()
            val connState2 = clientConnected.next()
            assertEquals(false, connState2.connected)
            assertEquals(serverPort, connState2.remoteAddress.port)
            val serverConn2 = serverConnected2.get()
            assertEquals(true, serverConn2.connected)
            assertEquals(BOB_NAME, CordaX500Name.build(serverConn2.remoteCert!!.subjectX500Principal))
            val connState3 = clientConnected.next()
            assertEquals(true, connState3.connected)
            assertEquals(ALICE_NAME, CordaX500Name.build(connState3.remoteCert!!.subjectX500Principal))
            assertEquals(serverPort2, connState3.remoteAddress.port)

            // Fail back
            amqpServer.start()
            amqpServer2.stop()
            val connState4 = clientConnected.next()
            assertEquals(false, connState4.connected)
            assertEquals(serverPort2, connState4.remoteAddress.port)
            val serverConn3 = serverConnected.get()
            assertEquals(true, serverConn3.connected)
            assertEquals(BOB_NAME, CordaX500Name.build(serverConn3.remoteCert!!.subjectX500Principal))
            val connState5 = clientConnected.next()
            assertEquals(true, connState5.connected)
            assertEquals(ALICE_NAME, CordaX500Name.build(connState5.remoteCert!!.subjectX500Principal))
            assertEquals(serverPort, connState5.remoteAddress.port)
        } finally {
            amqpClient.close()
            amqpServer.close()
            amqpServer2.close()
        }
    }

    @Test
    fun `Send a message from AMQP to Artemis inbox`() {
        val (server, artemisClient) = createArtemisServerAndClient()
        val amqpClient = createClient()
        val clientConnected = amqpClient.onConnection.toFuture()
        amqpClient.start()
        assertEquals(true, clientConnected.get().connected)
        assertEquals(CHARLIE_NAME, CordaX500Name.build(clientConnected.get().remoteCert!!.subjectX500Principal))
        val artemis = artemisClient.started!!
        val sendAddress = P2P_PREFIX + "Test"
        artemis.session.createQueue(sendAddress, RoutingType.ANYCAST, "queue", true)
        val consumer = artemis.session.createConsumer("queue")
        val testData = "Test".toByteArray()
        val testProperty = mutableMapOf<String, Any?>()
        testProperty["TestProp"] = "1"
        val message = amqpClient.createMessage(testData, sendAddress, CHARLIE_NAME.toString(), testProperty)
        amqpClient.write(message)
        assertEquals(MessageStatus.Acknowledged, message.onComplete.get())
        val received = consumer.receive()
        assertEquals("1", received.getStringProperty("TestProp"))
        assertArrayEquals(testData, ByteArray(received.bodySize).apply { received.bodyBuffer.readBytes(this) })
        amqpClient.stop()
        artemisClient.stop()
        server.stop()
    }

    @Test
    fun `Send a message larger then maxMessageSize from AMQP to Artemis inbox`() {
        val maxMessageSize = 100_000
        val (server, artemisClient) = createArtemisServerAndClient(maxMessageSize)
        val amqpClient = createClient(maxMessageSize)
        val clientConnected = amqpClient.onConnection.toFuture()
        amqpClient.start()
        assertEquals(true, clientConnected.get().connected)
        assertEquals(CHARLIE_NAME, CordaX500Name.build(clientConnected.get().remoteCert!!.subjectX500Principal))
        val artemis = artemisClient.started!!
        val sendAddress = P2P_PREFIX + "Test"
        artemis.session.createQueue(sendAddress, RoutingType.ANYCAST, "queue", true)
        val consumer = artemis.session.createConsumer("queue")

        val testProperty = mutableMapOf<String, Any?>()
        testProperty["TestProp"] = "1"

        // Send normal message.
        val testData = ByteArray(maxMessageSize)
        val message = amqpClient.createMessage(testData, sendAddress, CHARLIE_NAME.toString(), testProperty)
        amqpClient.write(message)
        assertEquals(MessageStatus.Acknowledged, message.onComplete.get())
        val received = consumer.receive()
        assertEquals("1", received.getStringProperty("TestProp"))
        assertArrayEquals(testData, ByteArray(received.bodySize).apply { received.bodyBuffer.readBytes(this) })

        // Send message larger then max message size.
        val largeData = ByteArray(maxMessageSize + 1)
        // Create message will fail.
        assertThatThrownBy {
            amqpClient.createMessage(largeData, sendAddress, CHARLIE_NAME.toString(), testProperty)
        }.hasMessageContaining("Message exceeds maxMessageSize network parameter")

        // Send normal message again to confirm the large message didn't reach the server and client is not killed by the message.
        val message2 = amqpClient.createMessage(testData, sendAddress, CHARLIE_NAME.toString(), testProperty)
        amqpClient.write(message2)
        assertEquals(MessageStatus.Acknowledged, message2.onComplete.get())
        val received2 = consumer.receive()
        assertEquals("1", received2.getStringProperty("TestProp"))
        assertArrayEquals(testData, ByteArray(received2.bodySize).apply { received2.bodyBuffer.readBytes(this) })

        amqpClient.stop()
        artemisClient.stop()
        server.stop()
    }

    @Test
    fun `shared AMQPClient threadpool tests`() {
        val amqpServer = createServer(serverPort)
        amqpServer.use {
            val connectionEvents = amqpServer.onConnection.toBlocking().iterator
            amqpServer.start()
            val sharedThreads = NioEventLoopGroup()
            val amqpClient1 = createSharedThreadsClient(sharedThreads, 0)
            val amqpClient2 = createSharedThreadsClient(sharedThreads, 1)
            amqpClient1.start()
            val connection1 = connectionEvents.next()
            assertEquals(true, connection1.connected)
            val connection1ID = CordaX500Name.build(connection1.remoteCert!!.subjectX500Principal)
            assertEquals("client 0", connection1ID.organisationUnit)
            val source1 = connection1.remoteAddress
            amqpClient2.start()
            val connection2 = connectionEvents.next()
            assertEquals(true, connection2.connected)
            val connection2ID = CordaX500Name.build(connection2.remoteCert!!.subjectX500Principal)
            assertEquals("client 1", connection2ID.organisationUnit)
            val source2 = connection2.remoteAddress
            // Stopping one shouldn't disconnect the other
            amqpClient1.stop()
            val connection3 = connectionEvents.next()
            assertEquals(false, connection3.connected)
            assertEquals(source1, connection3.remoteAddress)
            assertEquals(false, amqpClient1.connected)
            assertEquals(true, amqpClient2.connected)
            // Now shutdown both
            amqpClient2.stop()
            val connection4 = connectionEvents.next()
            assertEquals(false, connection4.connected)
            assertEquals(source2, connection4.remoteAddress)
            assertEquals(false, amqpClient1.connected)
            assertEquals(false, amqpClient2.connected)
            // Now restarting one should work
            amqpClient1.start()
            val connection5 = connectionEvents.next()
            assertEquals(true, connection5.connected)
            val connection5ID = CordaX500Name.build(connection5.remoteCert!!.subjectX500Principal)
            assertEquals("client 0", connection5ID.organisationUnit)
            assertEquals(true, amqpClient1.connected)
            assertEquals(false, amqpClient2.connected)
            // Cleanup
            amqpClient1.stop()
            sharedThreads.shutdownGracefully()
            sharedThreads.terminationFuture().sync()
        }
    }

    @Test
    fun `Message sent from AMQP to non-existent Artemis inbox is rejected and client disconnects`() {
        val (server, artemisClient) = createArtemisServerAndClient()
        val amqpClient = createClient()
        var connected = false
        amqpClient.onConnection.subscribe { change ->
            connected = change.connected
        }
        val clientConnected = amqpClient.onConnection.toFuture()
        amqpClient.start()
        assertEquals(true, clientConnected.get().connected)
        assertEquals(CHARLIE_NAME, CordaX500Name.build(clientConnected.get().remoteCert!!.subjectX500Principal))
        val sendAddress = P2P_PREFIX + "Test"
        val testData = "Test".toByteArray()
        val testProperty = mutableMapOf<String, Any?>()
        testProperty["TestProp"] = "1"
        val message = amqpClient.createMessage(testData, sendAddress, CHARLIE_NAME.toString(), testProperty)
        amqpClient.write(message)
        assertEquals(MessageStatus.Rejected, message.onComplete.get())
        assertEquals(false, connected)
        amqpClient.stop()
        artemisClient.stop()
        server.stop()
    }

    @Test
    fun `SNI AMQP client to SNI AMQP server`() {
        println(sslSetup)
        val amqpServer = createServerWithMultipleNames(serverPort, listOf(ALICE_NAME, CHARLIE_NAME))
        amqpServer.use {
            amqpServer.start()
            val receiveSubs = amqpServer.onReceive.subscribe {
                assertEquals(BOB_NAME.toString(), it.sourceLegalName)
                assertEquals(P2P_PREFIX + "Test", it.topic)
                assertEquals("Test", String(it.payload))
                it.complete(true)
            }
            createClient(MAX_MESSAGE_SIZE, setOf(ALICE_NAME)).use { amqpClient ->
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
                assertEquals(BOB_NAME, CordaX500Name.build(serverConnect.remoteCert!!.subjectX500Principal))
                val clientConnect = clientConnected.get()
                assertEquals(true, clientConnect.connected)
                assertEquals(ALICE_NAME, CordaX500Name.build(clientConnect.remoteCert!!.subjectX500Principal))
                val msg = amqpClient.createMessage("Test".toByteArray(),
                        P2P_PREFIX + "Test",
                        ALICE_NAME.toString(),
                        emptyMap())
                amqpClient.write(msg)
                assertEquals(MessageStatus.Acknowledged, msg.onComplete.get())

            }

            createClientWithMultipleCerts(listOf(BOC_NAME, BOB_NAME), BOB_NAME, setOf(ALICE_NAME)).use { amqpClient ->
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
                assertEquals(BOB_NAME, CordaX500Name.build(serverConnect.remoteCert!!.subjectX500Principal))
                val clientConnect = clientConnected.get()
                assertEquals(true, clientConnect.connected)
                assertEquals(ALICE_NAME, CordaX500Name.build(clientConnect.remoteCert!!.subjectX500Principal))
                val msg = amqpClient.createMessage("Test".toByteArray(),
                        P2P_PREFIX + "Test",
                        ALICE_NAME.toString(),
                        emptyMap())
                amqpClient.write(msg)
                assertEquals(MessageStatus.Acknowledged, msg.onComplete.get())
            }
            receiveSubs.unsubscribe()
        }
    }

    @Test
    fun `non-existent SNI AMQP client to SNI AMQP server with multiple identities`() {
        val amqpServer = createServerWithMultipleNames(serverPort, listOf(ALICE_NAME, CHARLIE_NAME))
        amqpServer.use {
            amqpServer.start()
            val amqpClient = createClientWithMultipleCerts(listOf(BOC_NAME, BOB_NAME), BOB_NAME, setOf(DUMMY_BANK_A_NAME))
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(false, serverConnect.connected)
                val clientConnect = clientConnected.get()
                assertEquals(false, clientConnect.connected)
            }
        }
    }

    private fun createArtemisServerAndClient(maxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val baseDirectory = temporaryFolder.root.toPath() / "artemis"
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, useOpenSsl = sslSetup.serverNative)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(CHARLIE_NAME).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(NetworkHostAndPort("0.0.0.0", artemisPort)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000))).whenever(it).enterpriseConfiguration
            doReturn(true).whenever(it).crlCheckSoftFail
        }
        artemisConfig.configureWithDevSSLCertificate()

        val server = ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", artemisPort), maxMessageSize)
        val client = ArtemisMessagingClient(artemisConfig.p2pSslOptions, NetworkHostAndPort("localhost", artemisPort), maxMessageSize)
        server.start()
        client.start()
        return Pair(server, client)
    }

    private fun createClient(maxMessageSize: Int = MAX_MESSAGE_SIZE,
                             expectedRemoteLegalNames: Set<CordaX500Name> = setOf(ALICE_NAME, CHARLIE_NAME)): AMQPClient {
        val baseDirectory = temporaryFolder.root.toPath() / "client"
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, useOpenSsl = sslSetup.clientNative)
        val clientConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(true).whenever(it).crlCheckSoftFail
        }
        clientConfig.configureWithDevSSLCertificate()

        val clientTruststore = clientConfig.p2pSslOptions.trustStore.get()
        val clientKeystore = clientConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = clientKeystore
            override val trustStore = clientTruststore
            override val trace: Boolean = true
            override val maxMessageSize: Int = maxMessageSize
            override val revocationConfig: RevocationConfig = clientConfig.crlCheckSoftFail.toRevocationConfig()
            override val sourceX500Name = BOB_NAME.toString()
            override val useOpenSsl: Boolean = sslSetup.clientNative
        }
        return AMQPClient(
                listOf(NetworkHostAndPort("localhost", serverPort),
                        NetworkHostAndPort("localhost", serverPort2),
                        NetworkHostAndPort("localhost", artemisPort)),
                expectedRemoteLegalNames,
                amqpConfig)
    }

    private fun createSharedThreadsClient(sharedEventGroup: EventLoopGroup, id: Int, maxMessageSize: Int = MAX_MESSAGE_SIZE): AMQPClient {
        val baseDirectory = temporaryFolder.root.toPath() / "client_%$id"
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, useOpenSsl = sslSetup.clientNative)
        val clientConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(CordaX500Name(null, "client $id", "Corda", "London", null, "GB")).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(true).whenever(it).crlCheckSoftFail
        }
        clientConfig.configureWithDevSSLCertificate()

        val clientTruststore = clientConfig.p2pSslOptions.trustStore.get()
        val clientKeystore = clientConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = clientKeystore
            override val trustStore = clientTruststore
            override val trace: Boolean = true
            override val maxMessageSize: Int = maxMessageSize
            override val revocationConfig: RevocationConfig = clientConfig.crlCheckSoftFail.toRevocationConfig()
            override val useOpenSsl: Boolean = sslSetup.clientNative
        }
        return AMQPClient(
                listOf(NetworkHostAndPort("localhost", serverPort)),
                setOf(ALICE_NAME),
                amqpConfig,
                sharedThreadPool = sharedEventGroup)
    }

    private fun createServer(port: Int,
                             name: CordaX500Name = ALICE_NAME,
                             maxMessageSize: Int = MAX_MESSAGE_SIZE,
                             crlCheckSoftFail: Boolean = true): AMQPServer {
        val baseDirectory = temporaryFolder.root.toPath() / "server"
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, useOpenSsl = sslSetup.serverNative)
        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(name).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
        }
        serverConfig.configureWithDevSSLCertificate()

        val serverTruststore = serverConfig.p2pSslOptions.trustStore.get()
        val serverKeystore = serverConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = serverKeystore
            override val trustStore = serverTruststore
            override val trace: Boolean = true
            override val maxMessageSize: Int = maxMessageSize
            override val revocationConfig: RevocationConfig = serverConfig.crlCheckSoftFail.toRevocationConfig()
            override val useOpenSsl: Boolean = sslSetup.serverNative
        }
        return AMQPServer(
                "0.0.0.0",
                port,
                amqpConfig)
    }

    private fun createAmqpConfigWithMultipleCerts(legalNames: List<CordaX500Name>,
                                                  sourceLegalName: String? = null,
                                                  maxMessageSize: Int = MAX_MESSAGE_SIZE,
                                                  crlCheckSoftFail: Boolean = true,
                                                  useOpenSsl: Boolean) :AMQPConfiguration {
        val tempFolders = legalNames.map { it to temporaryFolder.root.toPath() / it.organisation }.toMap()
        val baseDirectories = tempFolders.mapValues { it.value / "node" }
        val certificatesDirectories = baseDirectories.mapValues { it.value / "certificates" }
        val signingCertificateStores = certificatesDirectories.mapValues { CertificateStoreStubs.Signing.withCertificatesDirectory(it.value) }
        val pspSslConfigurations = certificatesDirectories.mapValues { CertificateStoreStubs.P2P.withCertificatesDirectory(it.value, useOpenSsl = sslSetup.serverNative) }
        val serverConfigs = legalNames.map { name ->
            val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
                doReturn(baseDirectories[name]).whenever(it).baseDirectory
                doReturn(certificatesDirectories[name]).whenever(it).certificatesDirectory
                doReturn(name).whenever(it).myLegalName
                doReturn(signingCertificateStores[name]).whenever(it).signingCertificateStore
                doReturn(pspSslConfigurations[name]).whenever(it).p2pSslOptions

                doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
            }
            serverConfig.configureWithDevSSLCertificate()
            serverConfig
        }

        val serverTruststore = serverConfigs.first().p2pSslOptions.trustStore.get(true)
        val serverKeystore = serverConfigs.first().p2pSslOptions.keyStore.get(true)
        // Merge rest of keystores into the first
        serverConfigs.subList(1, serverConfigs.size).forEach {
            mergeKeyStores(serverKeystore, it.p2pSslOptions.keyStore.get(true), it.myLegalName.toString())
        }

        return object : AMQPConfiguration {
            override val keyStore: CertificateStore = serverKeystore
            override val trustStore: CertificateStore = serverTruststore
            override val trace: Boolean = true
            override val maxMessageSize: Int = maxMessageSize
            override val useOpenSsl: Boolean = useOpenSsl
            override val sourceX500Name: String? = sourceLegalName
        }
    }

    private fun createServerWithMultipleNames(port: Int,
                                              serverNames: List<CordaX500Name>,
                                              maxMessageSize: Int = MAX_MESSAGE_SIZE,
                                              crlCheckSoftFail: Boolean = true): AMQPServer {
        return AMQPServer(
                "0.0.0.0",
                port,
                createAmqpConfigWithMultipleCerts(serverNames, null, maxMessageSize, crlCheckSoftFail, sslSetup.serverNative))
    }

    private fun createClientWithMultipleCerts(clientNames: List<CordaX500Name>,
                                              sourceLegalName: CordaX500Name,
                                              expectedRemoteLegalNames: Set<CordaX500Name> = setOf(ALICE_NAME, CHARLIE_NAME)): AMQPClient {
        return AMQPClient(
                listOf(NetworkHostAndPort("localhost", serverPort),
                        NetworkHostAndPort("localhost", serverPort2),
                        NetworkHostAndPort("localhost", artemisPort)),
                expectedRemoteLegalNames,
                createAmqpConfigWithMultipleCerts(clientNames, sourceLegalName.toString(), MAX_MESSAGE_SIZE, true, sslSetup.clientNative))
    }

    private fun mergeKeyStores(newKeyStore: CertificateStore, oldKeyStore: CertificateStore, newAlias: String) {
        val keyStore = oldKeyStore.value.internal
        keyStore.aliases().toList().forEach {
            val key = keyStore.getKey(it, oldKeyStore.password.toCharArray())
            val certs = keyStore.getCertificateChain(it)
            newKeyStore.value.internal.setKeyEntry(newAlias, key, oldKeyStore.password.toCharArray(), certs)
        }
    }
}
