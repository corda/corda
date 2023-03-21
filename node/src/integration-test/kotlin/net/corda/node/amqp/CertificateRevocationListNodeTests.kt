package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.times
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.days
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.coretesting.internal.DEV_INTERMEDIATE_CA
import net.corda.coretesting.internal.DEV_ROOT_CA
import net.corda.coretesting.internal.rigorousMock
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.*
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
import org.apache.activemq.artemis.api.core.RoutingType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.PrivateKey
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.test.assertEquals

@Suppress("LongParameterList")
class CertificateRevocationListNodeTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val ROOT_CA = DEV_ROOT_CA
    private lateinit var INTERMEDIATE_CA: CertificateAndKeyPair

    private val portAllocation = incrementalPortAllocation()
    private val serverPort = portAllocation.nextPort()

    private lateinit var crlServer: CrlServer
    private lateinit var amqpServer: AMQPServer
    private lateinit var amqpClient: AMQPClient

    private val revokedNodeCerts: MutableList<BigInteger> = mutableListOf()
    private val revokedIntermediateCerts: MutableList<BigInteger> = mutableListOf()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    companion object {
        const val FORBIDDEN_CRL = "forbidden.crl"

        private val unreachableIpCounter = AtomicInteger(1)

        private val crlTimeout = Duration.ofSeconds(System.getProperty("com.sun.security.crl.timeout").toLong())

        /**
         * Use this method to get a unqiue unreachable IP address. Subsequent uses of the same IP for connection timeout testing purposes
         * may not work as the OS process may cache the timeout result.
         */
        private fun newUnreachableIpAddress(): String {
            check(unreachableIpCounter.get() != 255)
            return "10.255.255.${unreachableIpCounter.getAndIncrement()}"
        }

        private fun createRevocationList(clrServer: CrlServer,
                                         signatureAlgorithm: String,
                                         caCertificate: X509Certificate,
                                         caPrivateKey: PrivateKey,
                                         endpoint: String,
                                         indirect: Boolean,
                                         serialNumbers: List<BigInteger>): X509CRL {
            println("Generating CRL for $endpoint")
            val builder = JcaX509v2CRLBuilder(caCertificate.subjectX500Principal, Date(System.currentTimeMillis() - 1.minutes.toMillis()))
            val extensionUtils = JcaX509ExtensionUtils()
            builder.addExtension(Extension.authorityKeyIdentifier,
                    false, extensionUtils.createAuthorityKeyIdentifier(caCertificate))
            val issuingDistPointName = GeneralName(
                    GeneralName.uniformResourceIdentifier,
                    "http://${clrServer.hostAndPort.host}:${clrServer.hostAndPort.port}/crl/$endpoint")
            // This is required and needs to match the certificate settings with respect to being indirect
            val issuingDistPoint = IssuingDistributionPoint(DistributionPointName(GeneralNames(issuingDistPointName)), indirect, false)
            builder.addExtension(Extension.issuingDistributionPoint, true, issuingDistPoint)
            builder.setNextUpdate(Date(System.currentTimeMillis() + 1.seconds.toMillis()))
            serialNumbers.forEach {
                builder.addCRLEntry(it, Date(System.currentTimeMillis() - 10.minutes.toMillis()), ReasonFlags.certificateHold)
            }
            val signer = JcaContentSignerBuilder(signatureAlgorithm).setProvider(Crypto.findProvider("BC")).build(caPrivateKey)
            return JcaX509CRLConverter().setProvider(Crypto.findProvider("BC")).getCRL(builder.build(signer))
        }
    }

    @Before
    fun setUp() {
        // Do not use Security.addProvider(BouncyCastleProvider()) to avoid EdDSA signature disruption in other tests.
        Crypto.findProvider(BouncyCastleProvider.PROVIDER_NAME)
        crlServer = CrlServer(NetworkHostAndPort("localhost", 0))
        crlServer.start()
        INTERMEDIATE_CA = CertificateAndKeyPair(replaceCrlDistPointCaCertificate(
                DEV_INTERMEDIATE_CA.certificate,
                CertificateType.INTERMEDIATE_CA,
                ROOT_CA.keyPair,
                "http://${crlServer.hostAndPort}/crl/intermediate.crl"), DEV_INTERMEDIATE_CA.keyPair)
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
                sslHandshakeTimeout = crlTimeout * 2,
                expectedConnectStatus = true
        )
        // We could use PKIXRevocationChecker.getSoftFailExceptions() to make sure timeout exceptions did actually occur, but the JDK seems
        // to have a bug in the older 8 builds where this method returns an empty list. Newer builds don't have this issue, but we need to
        // be able to support that certain minimum build.
    }

    @Test(timeout=300_000)
    fun `AMQP server connection fails when CRL endpoint is unreachable, despite soft fail enabled, when CRL timeouts are not within SSL handshake timeout`() {
        verifyAMQPConnection(
                crlCheckSoftFail = true,
                nodeCrlDistPoint = "http://${newUnreachableIpAddress()}/crl/unreachable.crl",
                sslHandshakeTimeout = crlTimeout / 2,
                expectedConnectStatus = false
        )
    }

    @Test(timeout=300_000)
	fun `verify CRL algorithms`() {
        val emptyCrl = "empty.crl"

        val crl = createRevocationList(
                crlServer,
                "SHA256withECDSA",
                ROOT_CA.certificate,
                ROOT_CA.keyPair.private,
                emptyCrl,
                true,
                emptyList()
        )
        // This should pass.
        crl.verify(ROOT_CA.keyPair.public)

        // Try changing the algorithm to EC will fail.
        assertThatIllegalArgumentException().isThrownBy {
            createRevocationList(
                    crlServer,
                    "EC",
                    ROOT_CA.certificate,
                    ROOT_CA.keyPair.private,
                    emptyCrl,
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
                sslHandshakeTimeout = crlTimeout * 3
        )
        // We could use PKIXRevocationChecker.getSoftFailExceptions() to make sure timeout exceptions did actually occur, but the JDK seems
        // to have a bug in the older 8 builds where this method returns an empty list. Newer builds don't have this issue, but we need to
        // be able to support that certain minimum build.
    }

    @Test(timeout = 300_000)
    fun `Artemis server connection fails with soft fail CRL check on unreachable URL if CRL timeout is not within SSL handshake timeout`() {
        verifyArtemisConnection(
                crlCheckSoftFail = true,
                crlCheckArtemisServer = true,
                expectedConnected = false,
                nodeCrlDistPoint = "http://${newUnreachableIpAddress()}/crl/unreachable.crl",
                sslHandshakeTimeout = crlTimeout / 2
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
                                 nodeCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/node.crl",
                                 tlsCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/empty.crl",
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
        val nodeCert = (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, tlsCrlDistPoint)
        val keyStore = clientConfig.p2pSslOptions.keyStore.get()

        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = clientConfig.p2pSslOptions.trustStore.get()
            override val maxMessageSize: Int = maxMessageSize
        }
        amqpClient = AMQPClient(listOf(NetworkHostAndPort("localhost", targetPort)), setOf(ALICE_NAME, CHARLIE_NAME), amqpConfig)

        return nodeCert
    }

    private fun createAMQPServer(port: Int, name: CordaX500Name = ALICE_NAME,
                                 crlCheckSoftFail: Boolean,
                                 nodeCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/node.crl",
                                 tlsCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/empty.crl",
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
        val nodeCert = (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, tlsCrlDistPoint)
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

    private fun Pair<CertificateStoreSupplier, MutualSslConfiguration>.recreateNodeCaAndTlsCertificates(nodeCaCrlDistPoint: String?, tlsCrlDistPoint: String?): X509Certificate {

        val signingCertificateStore = first
        val p2pSslConfiguration = second
        val nodeKeyStore = signingCertificateStore.get()
        val (nodeCert, nodeKeys) = nodeKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, nodeKeyStore.entryPassword) }
        val newNodeCert = replaceCrlDistPointCaCertificate(nodeCert, CertificateType.NODE_CA, INTERMEDIATE_CA.keyPair, nodeCaCrlDistPoint)
        val nodeCertChain = listOf(newNodeCert, INTERMEDIATE_CA.certificate, *nodeKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_CA) }.drop(2).toTypedArray())
        nodeKeyStore.update {
            internal.deleteEntry(X509Utilities.CORDA_CLIENT_CA)
        }
        nodeKeyStore.update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_CA, nodeKeys.private, nodeCertChain, nodeKeyStore.entryPassword)
        }
        val sslKeyStore = p2pSslConfiguration.keyStore.get()
        val (tlsCert, tlsKeys) = sslKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_TLS, sslKeyStore.entryPassword) }
        val newTlsCert = replaceCrlDistPointCaCertificate(tlsCert, CertificateType.TLS, nodeKeys, tlsCrlDistPoint, X500Name.getInstance(ROOT_CA.certificate.subjectX500Principal.encoded))
        val sslCertChain = listOf(newTlsCert, newNodeCert, INTERMEDIATE_CA.certificate, *sslKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_TLS) }.drop(3).toTypedArray())

        sslKeyStore.update {
            internal.deleteEntry(X509Utilities.CORDA_CLIENT_TLS)
        }
        sslKeyStore.update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeys.private, sslCertChain, sslKeyStore.entryPassword)
        }
        return newNodeCert
    }

    private fun replaceCrlDistPointCaCertificate(currentCaCert: X509Certificate, certType: CertificateType, issuerKeyPair: KeyPair, crlDistPoint: String?, crlIssuer: X500Name? = null): X509Certificate {
        val signatureScheme = Crypto.findSignatureScheme(issuerKeyPair.private)
        val provider = Crypto.findProvider(signatureScheme.providerName)
        val issuerSigner = ContentSignerBuilder.build(signatureScheme, issuerKeyPair.private, provider)
        val builder = X509Utilities.createPartialCertificate(
                certType,
                currentCaCert.issuerX500Principal,
                issuerKeyPair.public,
                currentCaCert.subjectX500Principal,
                currentCaCert.publicKey,
                Pair(Date(System.currentTimeMillis() - 5.minutes.toMillis()), Date(System.currentTimeMillis() + 10.days.toMillis())),
                null
        )
        crlDistPoint?.let {
            val distPointName = DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, it)))
            val crlIssuerGeneralNames = crlIssuer?.let {
                GeneralNames(GeneralName(crlIssuer))
            }
            val distPoint = DistributionPoint(distPointName, null, crlIssuerGeneralNames)
            builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(distPoint)))
        }
        return builder.build(issuerSigner).toJca()
    }

    @Path("crl")
    inner class CrlServlet(private val server: CrlServer) {

        private val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        @GET
        @Path("node.crl")
        @Produces("application/pkcs7-crl")
        fun getNodeCRL(): Response {
            return Response.ok(createRevocationList(
                    server,
                    SIGNATURE_ALGORITHM,
                    INTERMEDIATE_CA.certificate,
                    INTERMEDIATE_CA.keyPair.private,
                    "node.crl",
                    false,
                    revokedNodeCerts
            ).encoded).build()
        }

        @GET
        @Path(FORBIDDEN_CRL)
        @Produces("application/pkcs7-crl")
        fun getNodeSlowCRL(): Response {
            return Response.status(Response.Status.FORBIDDEN).build()
        }

        @GET
        @Path("intermediate.crl")
        @Produces("application/pkcs7-crl")
        fun getIntermediateCRL(): Response {
            return Response.ok(createRevocationList(
                    server,
                    SIGNATURE_ALGORITHM,
                    ROOT_CA.certificate,
                    ROOT_CA.keyPair.private,
                    "intermediate.crl",
                    false,
                    revokedIntermediateCerts
            ).encoded).build()
        }

        @GET
        @Path("empty.crl")
        @Produces("application/pkcs7-crl")
        fun getEmptyCRL(): Response {
            return Response.ok(createRevocationList(
                    server,
                    SIGNATURE_ALGORITHM,
                    ROOT_CA.certificate,
                    ROOT_CA.keyPair.private,
                    "empty.crl",
                    true, emptyList()
            ).encoded).build()
        }
    }

    inner class CrlServer(hostAndPort: NetworkHostAndPort) : Closeable {

        private val server: Server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
            handler = HandlerCollection().apply {
                addHandler(buildServletContextHandler())
            }
        }

        val hostAndPort: NetworkHostAndPort
            get() = server.connectors.mapNotNull { it as? ServerConnector }
                    .map { NetworkHostAndPort(it.host, it.localPort) }
                    .first()

        override fun close() {
            println("Shutting down network management web services...")
            server.stop()
            server.join()
        }

        fun start() {
            server.start()
            println("Network management web services started on $hostAndPort")
        }

        private fun buildServletContextHandler(): ServletContextHandler {
            val crlServer = this
            return ServletContextHandler().apply {
                contextPath = "/"
                val resourceConfig = ResourceConfig().apply {
                    register(CrlServlet(crlServer))
                }
                val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 }
                addServlet(jerseyServlet, "/*")
            }
        }
    }

    private fun verifyAMQPConnection(crlCheckSoftFail: Boolean,
                                     nodeCrlDistPoint: String? = "http://${crlServer.hostAndPort}/crl/node.crl",
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
            revokedNodeCerts.add(serverCert.serialNumber)
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
            revokedNodeCerts.add(clientCert.serialNumber)
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
        (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, null)

        val server = ArtemisMessagingServer(artemisConfig, artemisConfig.p2pAddress, MAX_MESSAGE_SIZE, null)
        val client = ArtemisMessagingClient(artemisConfig.p2pSslOptions, artemisConfig.p2pAddress, MAX_MESSAGE_SIZE)
        server.start()
        client.start()
        return server to client
    }

    private fun verifyArtemisConnection(crlCheckSoftFail: Boolean,
                                        crlCheckArtemisServer: Boolean,
                                        expectedConnected: Boolean = true,
                                        expectedStatus: MessageStatus? = null,
                                        revokedNodeCert: Boolean = false,
                                        nodeCrlDistPoint: String = "http://${crlServer.hostAndPort}/crl/node.crl",
                                        sslHandshakeTimeout: Duration? = null) {
        val queueName = P2P_PREFIX + "Test"
        val (artemisServer, artemisClient) = createArtemisServerAndClient(crlCheckSoftFail, crlCheckArtemisServer, nodeCrlDistPoint, sslHandshakeTimeout)
        artemisServer.use {
            artemisClient.started!!.session.createQueue(queueName, RoutingType.ANYCAST, queueName, true)

            val nodeCert = createAMQPClient(serverPort, true, nodeCrlDistPoint)
            if (revokedNodeCert) {
                revokedNodeCerts.add(nodeCert.serialNumber)
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
