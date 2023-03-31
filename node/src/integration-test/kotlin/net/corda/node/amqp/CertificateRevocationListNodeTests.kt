package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.rootCause
import net.corda.core.internal.times
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.coretesting.internal.rigorousMock
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.nodeapi.internal.protonwrapper.netty.toRevocationConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.internal.network.CrlServer
import net.corda.testing.node.internal.network.CrlServer.Companion.EMPTY_CRL
import net.corda.testing.node.internal.network.CrlServer.Companion.FORBIDDEN_CRL
import net.corda.testing.node.internal.network.CrlServer.Companion.NODE_CRL
import net.corda.testing.node.internal.network.CrlServer.Companion.withCrlDistPoint
import org.apache.activemq.artemis.api.core.RoutingType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class CertificateRevocationListNodeTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val portAllocation = incrementalPortAllocation()
    private val serverPort = portAllocation.nextPort()

    private lateinit var crlServer: CrlServer
    private lateinit var amqpServer: AMQPServer
    private lateinit var amqpClient: AMQPClient

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    companion object {
        private val unreachableIpCounter = AtomicInteger(1)

        private val crlConnectTimeout = Duration.ofMillis(System.getProperty("net.corda.dpcrl.connect.timeout").toLong())

        /**
         * Use this method to get a unqiue unreachable IP address. Subsequent uses of the same IP for connection timeout testing purposes
         * may not work as the OS process may cache the timeout result.
         */
        private fun newUnreachableIpAddress(): String {
            check(unreachableIpCounter.get() != 255)
            return "10.255.255.${unreachableIpCounter.getAndIncrement()}"
        }
    }

    @Before
    fun setUp() {
        // Do not use Security.addProvider(BouncyCastleProvider()) to avoid EdDSA signature disruption in other tests.
        Crypto.findProvider(BouncyCastleProvider.PROVIDER_NAME)
        crlServer = CrlServer(NetworkHostAndPort("localhost", 0))
        crlServer.start()
    }

    @After
    fun tearDown() {
        if (::amqpClient.isInitialized) {
            amqpClient.close()
        }
        if (::amqpServer.isInitialized) {
            amqpServer.close()
        }
        if (::crlServer.isInitialized) {
            crlServer.close()
        }
    }

    @Test(timeout=300_000)
	fun `AMQP server connection works and soft fail is enabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                expectedConnectStatus = true
        )
    }

    @Test(timeout=300_000)
	fun `AMQP server connection works and soft fail is disabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = false,
                expectedConnectStatus = true
        )
    }

    @Test(timeout=300_000)
	fun `AMQP server connection fails when client's certificate is revoked and soft fail is enabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                revokeClientCert = true,
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `AMQP server connection fails when client's certificate is revoked and soft fail is disabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = false,
                revokeClientCert = true,
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `AMQP server connection fails when servers's certificate is revoked and soft fail is enabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                revokeServerCert = true,
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
    fun `AMQP server connection fails when servers's certificate is revoked and soft fail is disabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = false,
                revokeServerCert = true,
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `AMQP server connection succeeds when CRL cannot be obtained and soft fail is enabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                nodeCrlDistPoint = "http://${crlServer.hostAndPort}/crl/invalid.crl",
                expectedConnectStatus = true
        )
    }

    @Test(timeout=300_000)
    fun `AMQP server connection fails when CRL cannot be obtained and soft fail is disabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = false,
                nodeCrlDistPoint = "http://${crlServer.hostAndPort}/crl/invalid.crl",
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
    fun `AMQP server connection succeeds when CRL is not defined and soft fail is enabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                nodeCrlDistPoint = null,
                expectedConnectStatus = true
        )
    }

    @Test(timeout=300_000)
	fun `AMQP server connection fails when CRL is not defined and soft fail is disabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = false,
                nodeCrlDistPoint = null,
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
    fun `AMQP server connection succeeds when CRL retrieval is forbidden and soft fail is enabled`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                nodeCrlDistPoint = "http://${crlServer.hostAndPort}/crl/$FORBIDDEN_CRL",
                expectedConnectStatus = true
        )
    }

    @Test(timeout=300_000)
    fun `AMQP server connection succeeds when CRL endpoint is unreachable, soft fail is enabled and CRL timeouts are within SSL handshake timeout`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                nodeCrlDistPoint = "http://${newUnreachableIpAddress()}/crl/unreachable.crl",
                sslHandshakeTimeout = crlConnectTimeout * 2,
                expectedConnectStatus = true
        )
        val timeoutExceptions = (amqpServer.softFailExceptions + amqpClient.softFailExceptions)
                .map { it.rootCause }
                .filterIsInstance<SocketTimeoutException>()
        assertThat(timeoutExceptions).isNotEmpty
    }

    @Test(timeout=300_000)
    fun `AMQP server connection fails when CRL endpoint is unreachable, despite soft fail enabled, when CRL timeouts are not within SSL handshake timeout`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                nodeCrlDistPoint = "http://${newUnreachableIpAddress()}/crl/unreachable.crl",
                sslHandshakeTimeout = crlConnectTimeout / 2,
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `verify CRL algorithms`() {
        val crl = crlServer.createRevocationList(
                "SHA256withECDSA",
                crlServer.rootCa,
                EMPTY_CRL,
                true,
                emptyList()
        )
        // This should pass.
        crl.verify(crlServer.rootCa.keyPair.public)

        // Try changing the algorithm to EC will fail.
        assertThatIllegalArgumentException().isThrownBy {
            crlServer.createRevocationList(
                    "EC",
                    crlServer.rootCa,
                    EMPTY_CRL,
                    true,
                    emptyList()
            )
        }.withMessage("Unknown signature type requested: EC")
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection succeeds with soft fail CRL check`() {
        verifyArtemisConnection(
                crlCheckSoftFail = true,
                crlCheckArtemisServer = true,
                expectedStatus = MessageStatus.Acknowledged
        )
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection succeeds with hard fail CRL check`() {
        verifyArtemisConnection(
                crlCheckSoftFail = false,
                crlCheckArtemisServer = true,
                expectedStatus = MessageStatus.Acknowledged
        )
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection succeeds with soft fail CRL check on unavailable URL`() {
        verifyArtemisConnection(
                crlCheckSoftFail = true,
                crlCheckArtemisServer = true,
                expectedStatus = MessageStatus.Acknowledged,
                nodeCrlDistPoint = "http://${crlServer.hostAndPort}/crl/$FORBIDDEN_CRL"
        )
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection succeeds with soft fail CRL check on unreachable URL if CRL timeout is within SSL handshake timeout`() {
        verifyArtemisConnection(
                crlCheckSoftFail = true,
                crlCheckArtemisServer = true,
                expectedStatus = MessageStatus.Acknowledged,
                nodeCrlDistPoint = "http://${newUnreachableIpAddress()}/crl/unreachable.crl",
                sslHandshakeTimeout = crlConnectTimeout * 3
        )
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection fails with soft fail CRL check on unreachable URL if CRL timeout is not within SSL handshake timeout`() {
        verifyArtemisConnection(
                crlCheckSoftFail = true,
                crlCheckArtemisServer = true,
                expectedConnected = false,
                nodeCrlDistPoint = "http://${newUnreachableIpAddress()}/crl/unreachable.crl",
                sslHandshakeTimeout = crlConnectTimeout / 2
        )
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection fails with hard fail CRL check on unavailable URL`() {
        verifyArtemisConnection(
                crlCheckSoftFail = false,
                crlCheckArtemisServer = true,
                expectedStatus = MessageStatus.Rejected,
                nodeCrlDistPoint = "http://${crlServer.hostAndPort}/crl/$FORBIDDEN_CRL"
        )
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection fails with soft fail CRL check on revoked node certificate`() {
        verifyArtemisConnection(
                crlCheckSoftFail = true,
                crlCheckArtemisServer = true,
                expectedStatus = MessageStatus.Rejected,
                revokedNodeCert = true
        )
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection succeeds with disabled CRL check on revoked node certificate`() {
        verifyArtemisConnection(
                crlCheckSoftFail = false,
                crlCheckArtemisServer = false,
                expectedStatus = MessageStatus.Acknowledged,
                revokedNodeCert = true
        )
    }

    private fun createAMQPClient(targetPort: Int,
                                 crlCheckSoftFail: Boolean,
                                 nodeCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/$NODE_CRL",
                                 tlsCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/$EMPTY_CRL",
                                 maxMessageSize: Int = MAX_MESSAGE_SIZE): X509Certificate {
        val baseDirectory = temporaryFolder.root.toPath() / "client"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val clientConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
        }
        clientConfig.configureWithDevSSLCertificate()
        val nodeCert = recreateNodeCaAndTlsCertificates(signingCertificateStore, p2pSslConfiguration, nodeCrlDistPoint, tlsCrlDistPoint)
        val keyStore = clientConfig.p2pSslOptions.keyStore.get()

        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = clientConfig.p2pSslOptions.trustStore.get()
            override val maxMessageSize: Int = maxMessageSize
        }
        amqpClient = AMQPClient(listOf(NetworkHostAndPort("localhost", targetPort)), setOf(ALICE_NAME, CHARLIE_NAME), amqpConfig)

        return nodeCert
    }

    @Suppress("LongParameterList")
    private fun createAMQPServer(port: Int,
                                 name: CordaX500Name = ALICE_NAME,
                                 crlCheckSoftFail: Boolean,
                                 nodeCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/$NODE_CRL",
                                 tlsCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/$EMPTY_CRL",
                                 maxMessageSize: Int = MAX_MESSAGE_SIZE,
                                 sslHandshakeTimeout: Duration? = null): X509Certificate {
        check(!::amqpServer.isInitialized)
        val baseDirectory = temporaryFolder.root.toPath() / "server"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(name).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
        }
        serverConfig.configureWithDevSSLCertificate()
        val nodeCert = recreateNodeCaAndTlsCertificates(signingCertificateStore, p2pSslConfiguration, nodeCrlDistPoint, tlsCrlDistPoint)
        val keyStore = serverConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = serverConfig.p2pSslOptions.trustStore.get()
            override val revocationConfig = crlCheckSoftFail.toRevocationConfig()
            override val maxMessageSize: Int = maxMessageSize
            override val sslHandshakeTimeout: Duration = sslHandshakeTimeout ?: super.sslHandshakeTimeout
        }
        amqpServer = AMQPServer("0.0.0.0", port, amqpConfig)
        return nodeCert
    }

    private fun recreateNodeCaAndTlsCertificates(signingCertificateStore: CertificateStoreSupplier,
                                                 p2pSslConfiguration: MutualSslConfiguration,
                                                 nodeCaCrlDistPoint: String?,
                                                 tlsCrlDistPoint: String?): X509Certificate {
        val nodeKeyStore = signingCertificateStore.get()
        val (nodeCert, nodeKeys) = nodeKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, nodeKeyStore.entryPassword) }
        val newNodeCert = crlServer.replaceNodeCertDistPoint(nodeCert, nodeCaCrlDistPoint)
        val nodeCertChain = listOf(newNodeCert, crlServer.intermediateCa.certificate) +
                nodeKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_CA) }.drop(2)

        nodeKeyStore.update {
            internal.deleteEntry(X509Utilities.CORDA_CLIENT_CA)
        }
        nodeKeyStore.update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_CA, nodeKeys.private, nodeCertChain, nodeKeyStore.entryPassword)
        }

        val sslKeyStore = p2pSslConfiguration.keyStore.get()
        val (tlsCert, tlsKeys) = sslKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_TLS, sslKeyStore.entryPassword) }
        val newTlsCert = tlsCert.withCrlDistPoint(nodeKeys, tlsCrlDistPoint, crlServer.rootCa.certificate.subjectX500Principal)
        val sslCertChain = listOf(newTlsCert, newNodeCert, crlServer.intermediateCa.certificate) +
                sslKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_TLS) }.drop(3)

        sslKeyStore.update {
            internal.deleteEntry(X509Utilities.CORDA_CLIENT_TLS)
        }
        sslKeyStore.update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeys.private, sslCertChain, sslKeyStore.entryPassword)
        }
        return newNodeCert
    }

    @Suppress("LongParameterList")
    private fun verifyAMQPConnection(crlCheckSoftFail: Boolean,
                                     nodeCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/$NODE_CRL",
                                     revokeServerCert: Boolean = false,
                                     revokeClientCert: Boolean = false,
                                     sslHandshakeTimeout: Duration? = null,
                                     expectedConnectStatus: Boolean) {
        val serverCert = createAMQPServer(
                serverPort,
                crlCheckSoftFail = crlCheckSoftFail,
                nodeCrlDistPoint = nodeCrlDistPoint,
                sslHandshakeTimeout = sslHandshakeTimeout
        )
        if (revokeServerCert) {
            crlServer.revokedNodeCerts.add(serverCert.serialNumber)
        }
        amqpServer.start()
        amqpServer.onReceive.subscribe {
            it.complete(true)
        }
        val clientCert = createAMQPClient(
                serverPort,
                crlCheckSoftFail = crlCheckSoftFail,
                nodeCrlDistPoint = nodeCrlDistPoint
        )
        if (revokeClientCert) {
            crlServer.revokedNodeCerts.add(clientCert.serialNumber)
        }
        val serverConnected = amqpServer.onConnection.toFuture()
        amqpClient.start()
        val serverConnect = serverConnected.get()
        assertThat(serverConnect.connected).isEqualTo(expectedConnectStatus)
    }

    private fun createArtemisServerAndClient(crlCheckSoftFail: Boolean,
                                             crlCheckArtemisServer: Boolean,
                                             nodeCrlDistPoint: String,
                                             sslHandshakeTimeout: Duration?): Pair<ArtemisMessagingServer, ArtemisMessagingClient> {
        val baseDirectory = temporaryFolder.root.toPath() / "artemis"
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, sslHandshakeTimeout = sslHandshakeTimeout)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(CHARLIE_NAME).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(NetworkHostAndPort("0.0.0.0", serverPort)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
            doReturn(crlCheckArtemisServer).whenever(it).crlCheckArtemisServer
        }
        artemisConfig.configureWithDevSSLCertificate()
        recreateNodeCaAndTlsCertificates(signingCertificateStore, p2pSslConfiguration, nodeCrlDistPoint, null)

        val server = ArtemisMessagingServer(artemisConfig, artemisConfig.p2pAddress, MAX_MESSAGE_SIZE, null)
        val client = ArtemisMessagingClient(artemisConfig.p2pSslOptions, artemisConfig.p2pAddress, MAX_MESSAGE_SIZE)
        server.start()
        client.start()
        return server to client
    }

    @Suppress("LongParameterList")
    private fun verifyArtemisConnection(crlCheckSoftFail: Boolean,
                                        crlCheckArtemisServer: Boolean,
                                        expectedConnected: Boolean = true,
                                        expectedStatus: MessageStatus? = null,
                                        revokedNodeCert: Boolean = false,
                                        nodeCrlDistPoint: String = "http://${crlServer.hostAndPort}/crl/$NODE_CRL",
                                        sslHandshakeTimeout: Duration? = null) {
        val queueName = P2P_PREFIX + "Test"
        val (artemisServer, artemisClient) = createArtemisServerAndClient(crlCheckSoftFail, crlCheckArtemisServer, nodeCrlDistPoint, sslHandshakeTimeout)
        artemisServer.use {
            artemisClient.started!!.session.createQueue(queueName, RoutingType.ANYCAST, queueName, true)

            val nodeCert = createAMQPClient(serverPort, true, nodeCrlDistPoint)
            if (revokedNodeCert) {
                crlServer.revokedNodeCerts.add(nodeCert.serialNumber)
            }
            val clientConnected = amqpClient.onConnection.toFuture()
            amqpClient.start()
            val clientConnect = clientConnected.get()
            assertThat(clientConnect.connected).isEqualTo(expectedConnected)

            if (expectedConnected) {
                val msg = amqpClient.createMessage("Test".toByteArray(), queueName, CHARLIE_NAME.toString(), emptyMap())
                amqpClient.write(msg)
                assertEquals(expectedStatus, msg.onComplete.get())
            }
            artemisClient.stop()
        }
    }
}
