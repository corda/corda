@file:Suppress("LongParameterList")

package net.corda.node.amqp

import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.times
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.coretesting.internal.rigorousMock
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import net.corda.nodeapi.internal.protonwrapper.netty.toRevocationConfig
import net.corda.nodeapi.internal.revocation.CertDistPointCrlSource
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.internal.network.CrlServer
import net.corda.testing.node.internal.network.CrlServer.Companion.EMPTY_CRL
import net.corda.testing.node.internal.network.CrlServer.Companion.NODE_CRL
import net.corda.testing.node.internal.network.CrlServer.Companion.withCrlDistPoint
import org.apache.activemq.artemis.api.core.QueueConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

abstract class AbstractServerRevocationTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val portAllocation = incrementalPortAllocation()
    protected val serverPort = portAllocation.nextPort()

    protected lateinit var crlServer: CrlServer
    private val amqpClients = ArrayList<AMQPClient>()

    protected lateinit var defaultCrlDistPoints: CrlDistPoints

    protected abstract class AbstractNodeConfiguration : NodeConfiguration

    companion object {
        private val unreachableIpCounter = AtomicInteger(1)

         val crlConnectTimeout = 2.seconds

        /**
         * Use this method to get a unqiue unreachable IP address. Subsequent uses of the same IP for connection timeout testing purposes
         * may not work as the OS process may cache the timeout result.
         */
        private fun newUnreachableIpAddress(): NetworkHostAndPort {
            check(unreachableIpCounter.get() != 255)
            return NetworkHostAndPort("10.255.255", unreachableIpCounter.getAndIncrement())
        }
    }

    @Before
    fun setUp() {
        // Do not use Security.addProvider(BouncyCastleProvider()) to avoid EdDSA signature disruption in other tests.
        Crypto.findProvider(BouncyCastleProvider.PROVIDER_NAME)
        crlServer = CrlServer(NetworkHostAndPort("localhost", 0))
        crlServer.start()
        defaultCrlDistPoints = CrlDistPoints(crlServer.hostAndPort)
    }

    @After
    fun tearDown() {
        amqpClients.parallelStream().forEach(AMQPClient::close)
        if (::crlServer.isInitialized) {
            crlServer.close()
        }
    }

    @Test(timeout=300_000)
	fun `connection succeeds when soft fail is enabled`() {
        verifyConnection(
                crlCheckSoftFail = true,
                expectedConnectedStatus = true
        )
    }

    @Test(timeout=300_000)
	fun `connection succeeds when soft fail is disabled`() {
        verifyConnection(
                crlCheckSoftFail = false,
                expectedConnectedStatus = true
        )
    }

    @Test(timeout=300_000)
	fun `connection fails when client's certificate is revoked and soft fail is enabled`() {
        verifyConnection(
                crlCheckSoftFail = true,
                revokeClientCert = true,
                expectedConnectedStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `connection fails when client's certificate is revoked and soft fail is disabled`() {
        verifyConnection(
                crlCheckSoftFail = false,
                revokeClientCert = true,
                expectedConnectedStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `connection fails when server's certificate is revoked and soft fail is enabled`() {
        verifyConnection(
                crlCheckSoftFail = true,
                revokeServerCert = true,
                expectedConnectedStatus = false
        )
    }

    @Test(timeout=300_000)
    fun `connection fails when server's certificate is revoked and soft fail is disabled`() {
        verifyConnection(
                crlCheckSoftFail = false,
                revokeServerCert = true,
                expectedConnectedStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `connection succeeds when CRL cannot be obtained and soft fail is enabled`() {
        verifyConnection(
                crlCheckSoftFail = true,
                clientCrlDistPoints = defaultCrlDistPoints.copy(nodeCa = "non-existent.crl"),
                expectedConnectedStatus = true
        )
    }

    @Test(timeout=300_000)
    fun `connection fails when CRL cannot be obtained and soft fail is disabled`() {
        verifyConnection(
                crlCheckSoftFail = false,
                clientCrlDistPoints = defaultCrlDistPoints.copy(nodeCa = "non-existent.crl"),
                expectedConnectedStatus = false
        )
    }

    @Test(timeout=300_000)
    fun `connection succeeds when CRL is not defined for node CA cert and soft fail is enabled`() {
        verifyConnection(
                crlCheckSoftFail = true,
                clientCrlDistPoints = defaultCrlDistPoints.copy(nodeCa = null),
                expectedConnectedStatus = true
        )
    }

    @Test(timeout=300_000)
	fun `connection fails when CRL is not defined for node CA cert and soft fail is disabled`() {
        verifyConnection(
                crlCheckSoftFail = false,
                clientCrlDistPoints = defaultCrlDistPoints.copy(nodeCa = null),
                expectedConnectedStatus = false
        )
    }

    @Test(timeout=300_000)
    fun `connection succeeds when CRL is not defined for TLS cert and soft fail is enabled`() {
        verifyConnection(
                crlCheckSoftFail = true,
                clientCrlDistPoints = defaultCrlDistPoints.copy(tls = null),
                expectedConnectedStatus = true
        )
    }

    @Test(timeout=300_000)
    fun `connection fails when CRL is not defined for TLS cert and soft fail is disabled`() {
        verifyConnection(
                crlCheckSoftFail = false,
                clientCrlDistPoints = defaultCrlDistPoints.copy(tls = null),
                expectedConnectedStatus = false
        )
    }

    @Test(timeout=300_000)
    fun `connection succeeds when CRL endpoint is unreachable, soft fail is enabled and CRL timeouts are within SSL handshake timeout`() {
        verifyConnection(
                crlCheckSoftFail = true,
                sslHandshakeTimeout = crlConnectTimeout * 4,
                clientCrlDistPoints = defaultCrlDistPoints.copy(crlServerAddress = newUnreachableIpAddress()),
                expectedConnectedStatus = true
        )
    }

    @Test(timeout=300_000)
    fun `connection fails when CRL endpoint is unreachable, despite soft fail enabled, when CRL timeouts are not within SSL handshake timeout`() {
        verifyConnection(
                crlCheckSoftFail = true,
                sslHandshakeTimeout = crlConnectTimeout / 2,
                clientCrlDistPoints = defaultCrlDistPoints.copy(crlServerAddress = newUnreachableIpAddress()),
                expectedConnectedStatus = false
        )
    }

    @Test(timeout = 300_000)
    fun `influx of new clients during CRL endpoint downtime does not cause existing connections to drop`() {
        val serverCrlSource = CertDistPointCrlSource()
        // Start the server and verify the first client has connected
        val firstClientConnectionChangeStatus = verifyConnection(
                crlCheckSoftFail = true,
                crlSource = serverCrlSource,
                // In general, N remoting threads will naturally support N-1 new handshaking clients plus one thread for heartbeating with
                // existing clients. The trick is to make sure at least N new clients are also supported.
                remotingThreads = 2,
                expectedConnectedStatus = true
        )

        // Now simulate the CRL endpoint becoming very slow/unreachable
        crlServer.delay = 10.minutes
        // And pretend enough time has elapsed that the cached CRLs have expired and need downloading again
        serverCrlSource.clearCache()

        // Now a bunch of new clients have arrived and want to handshake with the server, which will potentially cause the server's Netty
        // threads to be tied up in trying to download the CRLs.
        IntStream.range(0, 2).parallel().forEach { clientIndex ->
            val (newClient, _) = createAMQPClient(
                    serverPort,
                    crlCheckSoftFail = true,
                    legalName = CordaX500Name("NewClient$clientIndex", "London", "GB"),
                    crlDistPoints = defaultCrlDistPoints
            )
            newClient.start()
        }

        // Make sure there are no further connection change updates, i.e. the first client stays connected throughout this whole saga
        assertThat(firstClientConnectionChangeStatus.poll(30, TimeUnit.SECONDS)).isNull()
    }

    protected abstract fun verifyConnection(crlCheckSoftFail: Boolean,
                                            crlSource: CertDistPointCrlSource = CertDistPointCrlSource(connectTimeout = crlConnectTimeout),
                                            sslHandshakeTimeout: Duration? = null,
                                            remotingThreads: Int? = null,
                                            clientCrlDistPoints: CrlDistPoints = defaultCrlDistPoints,
                                            revokeClientCert: Boolean = false,
                                            revokeServerCert: Boolean = false,
                                            expectedConnectedStatus: Boolean): BlockingQueue<ConnectionChange>

    protected fun createAMQPClient(targetPort: Int,
                                   crlCheckSoftFail: Boolean,
                                   legalName: CordaX500Name,
                                   crlDistPoints: CrlDistPoints): Pair<AMQPClient, X509Certificate> {
        val baseDirectory = temporaryFolder.root.toPath() / legalName.organisation
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val clientConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(legalName).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
        }
        clientConfig.configureWithDevSSLCertificate()
        val nodeCert = crlDistPoints.recreateNodeCaAndTlsCertificates(signingCertificateStore, p2pSslConfiguration, crlServer)
        val keyStore = clientConfig.p2pSslOptions.keyStore.get()

        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = clientConfig.p2pSslOptions.trustStore.get()
            override val maxMessageSize: Int = MAX_MESSAGE_SIZE
            override val trace: Boolean = true
        }
        val amqpClient = AMQPClient(
                listOf(NetworkHostAndPort("localhost", targetPort)),
                setOf(CHARLIE_NAME),
                amqpConfig,
                nettyThreading = AMQPClient.NettyThreading.NonShared(legalName.organisation),
                distPointCrlSource = CertDistPointCrlSource(connectTimeout = crlConnectTimeout)
        )
        amqpClients += amqpClient
        return Pair(amqpClient, nodeCert)
    }

    protected fun AMQPClient.waitForInitialConnectionAndCaptureChanges(expectedConnectedStatus: Boolean): BlockingQueue<ConnectionChange> {
        val connectionChangeStatus = LinkedBlockingQueue<ConnectionChange>()
        onConnection.subscribe { connectionChangeStatus.add(it) }
        start()
        assertThat(connectionChangeStatus.take().connected).isEqualTo(expectedConnectedStatus)
        return connectionChangeStatus
    }

    protected data class CrlDistPoints(val crlServerAddress: NetworkHostAndPort,
                                       val nodeCa: String? = NODE_CRL,
                                       val tls: String? = EMPTY_CRL) {
        private val nodeCaCertCrlDistPoint: String? get() = nodeCa?.let { "http://$crlServerAddress/crl/$it" }
        private val tlsCertCrlDistPoint: String? get() = tls?.let { "http://$crlServerAddress/crl/$it" }

        fun recreateNodeCaAndTlsCertificates(signingCertificateStore: CertificateStoreSupplier,
                                             p2pSslConfiguration: MutualSslConfiguration,
                                             crlServer: CrlServer): X509Certificate {
            val nodeKeyStore = signingCertificateStore.get()
            val (nodeCert, nodeKeys) = nodeKeyStore.query { getCertificateAndKeyPair(CORDA_CLIENT_CA, nodeKeyStore.entryPassword) }
            val newNodeCert = crlServer.replaceNodeCertDistPoint(nodeCert, nodeCaCertCrlDistPoint)
            val nodeCertChain = listOf(newNodeCert, crlServer.intermediateCa.certificate) +
                    nodeKeyStore.query { getCertificateChain(CORDA_CLIENT_CA) }.drop(2)

            nodeKeyStore.update {
                internal.deleteEntry(CORDA_CLIENT_CA)
            }
            nodeKeyStore.update {
                setPrivateKey(CORDA_CLIENT_CA, nodeKeys.private, nodeCertChain, nodeKeyStore.entryPassword)
            }

            val sslKeyStore = p2pSslConfiguration.keyStore.get()
            val (tlsCert, tlsKeys) = sslKeyStore.query { getCertificateAndKeyPair(CORDA_CLIENT_TLS, sslKeyStore.entryPassword) }
            val newTlsCert = tlsCert.withCrlDistPoint(nodeKeys, tlsCertCrlDistPoint, crlServer.rootCa.certificate.subjectX500Principal)
            val sslCertChain = listOf(newTlsCert, newNodeCert, crlServer.intermediateCa.certificate) +
                    sslKeyStore.query { getCertificateChain(CORDA_CLIENT_TLS) }.drop(3)

            sslKeyStore.update {
                internal.deleteEntry(CORDA_CLIENT_TLS)
            }
            sslKeyStore.update {
                setPrivateKey(CORDA_CLIENT_TLS, tlsKeys.private, sslCertChain, sslKeyStore.entryPassword)
            }
            return newNodeCert
        }
    }
}


class AMQPServerRevocationTest : AbstractServerRevocationTest() {
    private lateinit var amqpServer: AMQPServer

    @After
    fun shutDown() {
        if (::amqpServer.isInitialized) {
            amqpServer.close()
        }
    }

    override fun verifyConnection(crlCheckSoftFail: Boolean,
                                  crlSource: CertDistPointCrlSource,
                                  sslHandshakeTimeout: Duration?,
                                  remotingThreads: Int?,
                                  clientCrlDistPoints: CrlDistPoints,
                                  revokeClientCert: Boolean,
                                  revokeServerCert: Boolean,
                                  expectedConnectedStatus: Boolean): BlockingQueue<ConnectionChange> {
        val serverCert = createAMQPServer(
                serverPort,
                CHARLIE_NAME,
                crlCheckSoftFail,
                defaultCrlDistPoints,
                crlSource,
                sslHandshakeTimeout,
                remotingThreads
        )
        if (revokeServerCert) {
            crlServer.revokedNodeCerts.add(serverCert)
        }
        amqpServer.start()
        amqpServer.onReceive.subscribe {
            it.complete(true)
        }
        val (client, clientCert) = createAMQPClient(
                serverPort,
                crlCheckSoftFail = crlCheckSoftFail,
                legalName = ALICE_NAME,
                crlDistPoints = clientCrlDistPoints
        )
        if (revokeClientCert) {
            crlServer.revokedNodeCerts.add(clientCert)
        }

        return client.waitForInitialConnectionAndCaptureChanges(expectedConnectedStatus)
    }

    private fun createAMQPServer(port: Int,
                                 legalName: CordaX500Name,
                                 crlCheckSoftFail: Boolean,
                                 crlDistPoints: CrlDistPoints,
                                 distPointCrlSource: CertDistPointCrlSource,
                                 sslHandshakeTimeout: Duration?,
                                 remotingThreads: Int?): X509Certificate {
        check(!::amqpServer.isInitialized)
        val baseDirectory = temporaryFolder.root.toPath() / legalName.organisation
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(legalName).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
        }
        serverConfig.configureWithDevSSLCertificate()
        val serverCert = crlDistPoints.recreateNodeCaAndTlsCertificates(signingCertificateStore, p2pSslConfiguration, crlServer)
        val keyStore = serverConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = serverConfig.p2pSslOptions.trustStore.get()
            override val revocationConfig = crlCheckSoftFail.toRevocationConfig()
            override val maxMessageSize: Int = MAX_MESSAGE_SIZE
            override val sslHandshakeTimeout: Duration = sslHandshakeTimeout ?: super.sslHandshakeTimeout
        }
        amqpServer = AMQPServer(
                "0.0.0.0",
                port,
                amqpConfig,
                threadPoolName = legalName.organisation,
                distPointCrlSource = distPointCrlSource,
                remotingThreads = remotingThreads
        )
        return serverCert
    }
}


class ArtemisServerRevocationTest : AbstractServerRevocationTest() {
    private lateinit var artemisNode: ArtemisNode
    private var crlCheckArtemisServer = true

    @After
    fun shutDown() {
        if (::artemisNode.isInitialized) {
            artemisNode.close()
        }
    }

    @Test(timeout = 300_000)
    fun `connection succeeds with disabled CRL check on revoked node certificate`() {
        crlCheckArtemisServer = false
        verifyConnection(
                crlCheckSoftFail = false,
                revokeClientCert = true,
                expectedConnectedStatus = true
        )
    }

    override fun verifyConnection(crlCheckSoftFail: Boolean,
                                  crlSource: CertDistPointCrlSource,
                                  sslHandshakeTimeout: Duration?,
                                  remotingThreads: Int?,
                                  clientCrlDistPoints: CrlDistPoints,
                                  revokeClientCert: Boolean,
                                  revokeServerCert: Boolean,
                                  expectedConnectedStatus: Boolean): BlockingQueue<ConnectionChange> {
        val (client, clientCert) = createAMQPClient(
                serverPort,
                crlCheckSoftFail = true,
                legalName = ALICE_NAME,
                crlDistPoints = clientCrlDistPoints
        )
        if (revokeClientCert) {
            crlServer.revokedNodeCerts.add(clientCert)
        }

        val nodeCert = startArtemisNode(
                CHARLIE_NAME,
                crlCheckSoftFail,
                defaultCrlDistPoints,
                crlSource,
                sslHandshakeTimeout,
                remotingThreads
        )
        if (revokeServerCert) {
            crlServer.revokedNodeCerts.add(nodeCert)
        }

        val queueName = "${P2P_PREFIX}Test"
        artemisNode.client.started!!.session.createQueue(
                QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setAddress(queueName).setDurable(true)
        )

        val clientConnectionChangeStatus = client.waitForInitialConnectionAndCaptureChanges(expectedConnectedStatus)

        if (expectedConnectedStatus) {
            val msg = client.createMessage("Test".toByteArray(), queueName, CHARLIE_NAME.toString(), emptyMap())
            client.write(msg)
            assertThat(msg.onComplete.get()).isEqualTo(MessageStatus.Acknowledged)
        }

        return clientConnectionChangeStatus
    }

    private fun startArtemisNode(legalName: CordaX500Name,
                                 crlCheckSoftFail: Boolean,
                                 crlDistPoints: CrlDistPoints,
                                 distPointCrlSource: CertDistPointCrlSource,
                                 sslHandshakeTimeout: Duration?,
                                 remotingThreads: Int?): X509Certificate {
        check(!::artemisNode.isInitialized)
        val baseDirectory = temporaryFolder.root.toPath() / legalName.organisation
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory, sslHandshakeTimeout = sslHandshakeTimeout)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(legalName).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslConfiguration).whenever(it).p2pSslOptions
            doReturn(NetworkHostAndPort("0.0.0.0", serverPort)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
            doReturn(crlCheckArtemisServer).whenever(it).crlCheckArtemisServer
        }
        artemisConfig.configureWithDevSSLCertificate()
        val nodeCert = crlDistPoints.recreateNodeCaAndTlsCertificates(signingCertificateStore, p2pSslConfiguration, crlServer)

        val server = ArtemisMessagingServer(
                artemisConfig,
                artemisConfig.p2pAddress,
                MAX_MESSAGE_SIZE,
                threadPoolName = "${legalName.organisation}-server",
                trace = true,
                distPointCrlSource = distPointCrlSource,
                remotingThreads = remotingThreads
        )
        val client = ArtemisMessagingClient(
                artemisConfig.p2pSslOptions,
                artemisConfig.p2pAddress,
                MAX_MESSAGE_SIZE,
                threadPoolName = "${legalName.organisation}-client"
        )
        server.start()
        client.start()
        val artemisNode = ArtemisNode(server, client)
        this.artemisNode = artemisNode
        return nodeCert
    }

    private class ArtemisNode(val server: ArtemisMessagingServer, val client: ArtemisMessagingClient) : Closeable {
        override fun close() {
            client.stop()
            server.close()
        }
    }
}
