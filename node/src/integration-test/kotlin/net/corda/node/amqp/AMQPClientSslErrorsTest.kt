package net.corda.node.amqp

import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionResult
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfigImpl
import net.corda.nodeapi.internal.protonwrapper.netty.keyManagerFactory
import net.corda.nodeapi.internal.protonwrapper.netty.toRevocationConfig
import net.corda.nodeapi.internal.protonwrapper.netty.trustManagerFactoryWithRevocation
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.fixedCrlSource
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Duration
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.io.path.div
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * This test verifies some edge case scenarios like handshake timeouts when [AMQPClient] connected to the server
 *
 * In order to have control over handshake internals a simple TLS server is created which may have a configurable handshake delay.
 */
@Ignore  // These tests were disabled for JDK11+ very shortly after being introduced (https://github.com/corda/corda/pull/6560)
@RunWith(Parameterized::class)
class AMQPClientSslErrorsTest(@Suppress("unused") private val iteration: Int) {

    companion object {
        private const val MAX_MESSAGE_SIZE = 10 * 1024
        private val log = contextLogger()

        @JvmStatic
        @Parameterized.Parameters(name = "iteration = {0}")
        fun iterations(): Iterable<Array<Int>> {
            // It is possible to change this value to a greater number
            // to ensure that the test is not flaking when executed on CI
            val repsCount = 1
            return (1..repsCount).map { arrayOf(it) }
        }
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val portAllocation = incrementalPortAllocation()

    private lateinit var serverKeyManagerFactory: KeyManagerFactory
    private lateinit var serverTrustManagerFactory: TrustManagerFactory

    private lateinit var clientKeyManagerFactory: KeyManagerFactory
    private lateinit var clientTrustManagerFactory: TrustManagerFactory

    private lateinit var clientAmqpConfig: AMQPConfiguration

    @Before
    fun setup() {
        setupServerCertificates()
        setupClientCertificates()
    }

    private fun setupServerCertificates() {
        val baseDirectory = temporaryFolder.root.toPath() / "server"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val serverConfig = mock<NodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
        }
        serverConfig.configureWithDevSSLCertificate()
        val keyStore = serverConfig.p2pSslOptions.keyStore.get()
        val serverAmqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = serverConfig.p2pSslOptions.trustStore.get()
            override val revocationConfig = true.toRevocationConfig()
            override val maxMessageSize: Int = MAX_MESSAGE_SIZE
        }

        serverKeyManagerFactory = keyManagerFactory(keyStore)

        serverTrustManagerFactory = trustManagerFactoryWithRevocation(
                serverAmqpConfig.trustStore,
                RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL),
                fixedCrlSource(emptySet())
        )
    }

    private fun setupClientCertificates() {
        val baseDirectory = temporaryFolder.root.toPath() / "client"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val clientConfig = mock<NodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(true).whenever(it).crlCheckSoftFail
        }
        clientConfig.configureWithDevSSLCertificate()
        //val nodeCert = (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, tlsCrlDistPoint)
        val keyStore = clientConfig.p2pSslOptions.keyStore.get()

        clientAmqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = clientConfig.p2pSslOptions.trustStore.get()
            override val maxMessageSize: Int = MAX_MESSAGE_SIZE
            override val sslHandshakeTimeout: Duration = 3.seconds
        }

        clientKeyManagerFactory = keyManagerFactory(keyStore)

        clientTrustManagerFactory = trustManagerFactoryWithRevocation(
                clientAmqpConfig.trustStore,
                RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL),
                fixedCrlSource(emptySet())
        )
    }

    @Test(timeout = 300_000)
    fun `trivial client server exchange`() {
        val serverPort = portAllocation.nextPort()
        val serverThread = ServerThread(serverKeyManagerFactory, serverTrustManagerFactory, serverPort).also { it.start() }

        //System.setProperty("javax.net.debug", "all");

        serverThread.use {
            val client = NioSslClient(clientKeyManagerFactory, clientTrustManagerFactory, "localhost", serverPort)
            client.connect()
            client.write("Hello! I am a client!")
            client.read()
            client.shutdown()

            val client2 = NioSslClient(clientKeyManagerFactory, clientTrustManagerFactory, "localhost", serverPort)
            val client3 = NioSslClient(clientKeyManagerFactory, clientTrustManagerFactory, "localhost", serverPort)
            val client4 = NioSslClient(clientKeyManagerFactory, clientTrustManagerFactory, "localhost", serverPort)

            client2.connect()
            client2.write("Hello! I am another client!")
            client2.read()
            client2.shutdown()

            client3.connect()
            client4.connect()
            client3.write("Hello from client3!!!")
            client4.write("Hello from client4!!!")
            client3.read()
            client4.read()
            client3.shutdown()
            client4.shutdown()
        }
        assertFalse(serverThread.isActive)
    }

    @Test(timeout = 300_000)
    fun `amqp client server connect`() {
        val serverPort = portAllocation.nextPort()
        val serverThread = ServerThread(serverKeyManagerFactory, serverTrustManagerFactory, serverPort)
                .also { it.start() }
        serverThread.use {
            val amqpClient = AMQPClient(listOf(NetworkHostAndPort("localhost", serverPort)), setOf(ALICE_NAME), clientAmqpConfig)

            amqpClient.use {
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val clientConnect = clientConnected.get()
                assertTrue(clientConnect.connected)

                log.info("Confirmed connected")
            }
        }
        assertFalse(serverThread.isActive)
    }

    @Test(timeout = 300_000)
    fun `amqp client server handshake timeout`() {
        val serverPort = portAllocation.nextPort()
        val serverThread = ServerThread(serverKeyManagerFactory, serverTrustManagerFactory, serverPort, 5.seconds)
                .also { it.start() }
        serverThread.use {
            val amqpClient = AMQPClient(listOf(NetworkHostAndPort("localhost", serverPort)), setOf(ALICE_NAME), clientAmqpConfig)

            amqpClient.use {
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val clientConnect = clientConnected.get()
                assertFalse(clientConnect.connected)
                // Not a badCert, but a timeout during handshake
                assertEquals(ConnectionResult.NO_ERROR, clientConnect.connectionResult)
            }
        }
        assertFalse(serverThread.isActive)
    }
}
