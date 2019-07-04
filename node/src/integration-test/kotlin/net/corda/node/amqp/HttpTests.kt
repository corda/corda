package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.*
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.eclipse.jetty.proxy.ConnectHandler
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletContextHandler.SESSIONS
import org.eclipse.jetty.servlet.ServletHolder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class HttpTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val portAllocator = incrementalPortAllocation()
    private val httpProxyPort = portAllocator.nextPort()
    private val serverPort = portAllocator.nextPort()
    private val serverPort2 = portAllocator.nextPort()
    private val artemisPort = portAllocator.nextPort()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    private val httpProxy: Server = reverseJettyProxy()

    private fun reverseJettyProxy() : Server {
        val server = Server()

        val connector = ServerConnector(server)
        connector.host = "localhost"
        connector.port = httpProxyPort

        server.connectors = arrayOf<Connector>(connector)

        // Setup proxy handler to handle CONNECT methods
        val proxy = ConnectHandler()
        server.handler = proxy

        // Setup proxy servlet
        val context = ServletContextHandler(proxy, "/", SESSIONS)
        val proxyServlet = ServletHolder(ProxyServlet.Transparent::class.java)
        proxyServlet.setInitParameter("ProxyTo", "localhost:$serverPort")
        proxyServlet.setInitParameter("Prefix", "/")
        context.addServlet(proxyServlet, "/*")

        return server
    }

    @Before
    fun setup() {
        httpProxy.start()
    }

    @After
    fun shutdown() {
        httpProxy.stop()
    }

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

    private fun createClient(): AMQPClient {
        val baseDirectory = temporaryFolder.root.toPath() / "client"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)

        val clientConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(temporaryFolder.root.toPath() / "client").whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
        }
        clientConfig.configureWithDevSSLCertificate()

        val clientTruststore = clientConfig.p2pSslOptions.trustStore.get()
        val clientKeystore = clientConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = clientKeystore
            override val trustStore = clientTruststore
            override val trace: Boolean = true
            override val maxMessageSize: Int = MAX_MESSAGE_SIZE
            override val proxyConfig: ProxyConfig? = ProxyConfig(ProxyVersion.HTTP, NetworkHostAndPort("127.0.0.1", httpProxyPort), null, null)
        }
        return AMQPClient(
                listOf(NetworkHostAndPort("localhost", serverPort),
                        NetworkHostAndPort("localhost", serverPort2),
                        NetworkHostAndPort("localhost", artemisPort)),
                setOf(ALICE_NAME, CHARLIE_NAME),
                amqpConfig)
    }

    private fun createServer(port: Int, name: CordaX500Name = ALICE_NAME): AMQPServer {
        val baseDirectory = temporaryFolder.root.toPath() / "server"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)

        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(name).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
        }
        serverConfig.configureWithDevSSLCertificate()

        val serverTruststore = serverConfig.p2pSslOptions.trustStore.get()
        val serverKeystore = serverConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = serverKeystore
            override val trustStore = serverTruststore
            override val trace: Boolean = true
            override val maxMessageSize: Int = MAX_MESSAGE_SIZE
        }
        return AMQPServer(
                "0.0.0.0",
                port,
                amqpConfig)
    }
}
