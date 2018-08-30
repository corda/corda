package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.days
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.TwoWaySslConfiguration
import net.corda.nodeapi.internal.crypto.*
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.stubs.CertificateStoreStubs
import net.corda.testing.internal.DEV_INTERMEDIATE_CA
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.rigorousMock
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
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.test.assertEquals

class CertificateRevocationListNodeTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val ROOT_CA = DEV_ROOT_CA
    private lateinit var INTERMEDIATE_CA: CertificateAndKeyPair

    private val portAllocation = PortAllocation.Incremental(10000)
    private val serverPort = portAllocation.nextPort()

    private lateinit var server: CrlServer

    private val revokedNodeCerts: MutableList<BigInteger> = mutableListOf()
    private val revokedIntermediateCerts: MutableList<BigInteger> = mutableListOf()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Before
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        revokedNodeCerts.clear()
        server = CrlServer(NetworkHostAndPort("localhost", 0))
        server.start()
        INTERMEDIATE_CA = CertificateAndKeyPair(replaceCrlDistPointCaCertificate(
                DEV_INTERMEDIATE_CA.certificate,
                CertificateType.INTERMEDIATE_CA,
                ROOT_CA.keyPair,
                "http://${server.hostAndPort}/crl/intermediate.crl"), DEV_INTERMEDIATE_CA.keyPair)
    }

    @After
    fun tearDown() {
        server.close()
        revokedNodeCerts.clear()
    }

    @Test
    fun `Simple AMPQ Client to Server connection works and soft fail is enabled`() {
        val crlCheckSoftFail = true
        val (amqpServer, _) = createServer(serverPort, crlCheckSoftFail = crlCheckSoftFail)
        amqpServer.use {
            amqpServer.start()
            val receiveSubs = amqpServer.onReceive.subscribe {
                assertEquals(BOB_NAME.toString(), it.sourceLegalName)
                assertEquals(P2P_PREFIX + "Test", it.topic)
                assertEquals("Test", String(it.payload))
                it.complete(true)
            }
            val (amqpClient, _) = createClient(serverPort, crlCheckSoftFail)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
                val clientConnect = clientConnected.get()
                assertEquals(true, clientConnect.connected)
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
    fun `Simple AMPQ Client to Server connection works and soft fail is disabled`() {
        val crlCheckSoftFail = false
        val (amqpServer, _) = createServer(serverPort, crlCheckSoftFail = crlCheckSoftFail)
        amqpServer.use {
            amqpServer.start()
            val receiveSubs = amqpServer.onReceive.subscribe {
                assertEquals(BOB_NAME.toString(), it.sourceLegalName)
                assertEquals(P2P_PREFIX + "Test", it.topic)
                assertEquals("Test", String(it.payload))
                it.complete(true)
            }
            val (amqpClient, _) = createClient(serverPort, crlCheckSoftFail)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
                val clientConnect = clientConnected.get()
                assertEquals(true, clientConnect.connected)
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
    fun `AMPQ Client to Server connection fails when client's certificate is revoked and soft fail is enabled`() {
        val crlCheckSoftFail = true
        val (amqpServer, _) = createServer(serverPort, crlCheckSoftFail = crlCheckSoftFail)
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, clientCert) = createClient(serverPort, crlCheckSoftFail)
            revokedNodeCerts.add(clientCert.serialNumber)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(false, serverConnect.connected)
            }
        }
    }

    @Test
    fun `AMPQ Client to Server connection fails when client's certificate is revoked and soft fail is disabled`() {
        val crlCheckSoftFail = false
        val (amqpServer, _) = createServer(serverPort, crlCheckSoftFail = crlCheckSoftFail)
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, clientCert) = createClient(serverPort, crlCheckSoftFail)
            revokedNodeCerts.add(clientCert.serialNumber)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(false, serverConnect.connected)
            }
        }
    }

    @Test
    fun `AMPQ Client to Server connection fails when servers's certificate is revoked`() {
        val crlCheckSoftFail = true
        val (amqpServer, serverCert) = createServer(serverPort, crlCheckSoftFail = crlCheckSoftFail)
        revokedNodeCerts.add(serverCert.serialNumber)
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, _) = createClient(serverPort, crlCheckSoftFail)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(false, serverConnect.connected)
            }
        }
    }

    @Test
    fun `AMPQ Client to Server connection fails when servers's certificate is revoked and soft fail is enabled`() {
        val crlCheckSoftFail = true
        val (amqpServer, serverCert) = createServer(serverPort, crlCheckSoftFail = crlCheckSoftFail)
        revokedNodeCerts.add(serverCert.serialNumber)
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, _) = createClient(serverPort, crlCheckSoftFail)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(false, serverConnect.connected)
            }
        }
    }

    @Test
    fun `AMPQ Client to Server connection succeeds when CRL cannot be obtained and soft fail is enabled`() {
        val crlCheckSoftFail = true
        val (amqpServer, _) = createServer(
                serverPort,
                crlCheckSoftFail = crlCheckSoftFail,
                nodeCrlDistPoint = "http://${server.hostAndPort}/crl/invalid.crl")
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, _) = createClient(
                    serverPort,
                    crlCheckSoftFail,
                    nodeCrlDistPoint = "http://${server.hostAndPort}/crl/invalid.crl")
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
            }
        }
    }

    @Test
    fun `Revocation status chceck fails when the CRL distribution point is not set and soft fail is disabled`() {
        val crlCheckSoftFail = false
        val (amqpServer, _) = createServer(
                serverPort,
                crlCheckSoftFail = crlCheckSoftFail,
                tlsCrlDistPoint = null)
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, _) = createClient(
                    serverPort,
                    crlCheckSoftFail,
                    tlsCrlDistPoint = null)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(false, serverConnect.connected)
            }
        }
    }

    @Test
    fun `Revocation status chceck succeds when the CRL distribution point is not set and soft fail is enabled`() {
        val crlCheckSoftFail = true
        val (amqpServer, _) = createServer(
                serverPort,
                crlCheckSoftFail = crlCheckSoftFail,
                tlsCrlDistPoint = null)
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, _) = createClient(
                    serverPort,
                    crlCheckSoftFail,
                    tlsCrlDistPoint = null)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
            }
        }
    }

    private fun createClient(targetPort: Int,
                             crlCheckSoftFail: Boolean,
                             nodeCrlDistPoint: String = "http://${server.hostAndPort}/crl/node.crl",
                             tlsCrlDistPoint: String? = "http://${server.hostAndPort}/crl/empty.crl",
                             maxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<AMQPClient, X509Certificate> {
        val baseDirectory = temporaryFolder.root.toPath() / "client"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val clientConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(BOB_NAME).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslConfiguration
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
        }
        clientConfig.configureWithDevSSLCertificate()
        val nodeCert = (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, tlsCrlDistPoint)
        val keyStore = clientConfig.p2pSslConfiguration.keyStore.get()
        val clientTruststore = clientConfig.p2pSslConfiguration.trustStore.get().value.internal
        val clientKeystore = keyStore.value.internal

        val amqpConfig = object : AMQPConfiguration {
            override val keyStore: KeyStore = clientKeystore
            override val keyStorePrivateKeyPassword: CharArray = keyStore.password.toCharArray()
            override val trustStore: KeyStore = clientTruststore
            override val crlCheckSoftFail: Boolean = crlCheckSoftFail
            override val maxMessageSize: Int = maxMessageSize
        }
        return Pair(AMQPClient(
                listOf(NetworkHostAndPort("localhost", targetPort)),
                setOf(ALICE_NAME, CHARLIE_NAME),
                amqpConfig), nodeCert)
    }

    private fun createServer(port: Int, name: CordaX500Name = ALICE_NAME,
                             crlCheckSoftFail: Boolean,
                             nodeCrlDistPoint: String = "http://${server.hostAndPort}/crl/node.crl",
                             tlsCrlDistPoint: String? = "http://${server.hostAndPort}/crl/empty.crl",
                             maxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<AMQPServer, X509Certificate> {
        val baseDirectory = temporaryFolder.root.toPath() / "server"
        val certificatesDirectory = baseDirectory / "certificates"
        val p2pSslConfiguration = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val serverConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(name).whenever(it).myLegalName
            doReturn(p2pSslConfiguration).whenever(it).p2pSslConfiguration
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(crlCheckSoftFail).whenever(it).crlCheckSoftFail
        }
        serverConfig.configureWithDevSSLCertificate()
        val nodeCert = (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, tlsCrlDistPoint)
        val keyStore = serverConfig.p2pSslConfiguration.keyStore.get()
        val serverTruststore = serverConfig.p2pSslConfiguration.trustStore.get().value.internal
        val serverKeystore = keyStore.value.internal
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore: KeyStore = serverKeystore
            override val keyStorePrivateKeyPassword: CharArray = keyStore.password.toCharArray()
            override val trustStore: KeyStore = serverTruststore
            override val crlCheckSoftFail: Boolean = crlCheckSoftFail
            override val maxMessageSize: Int = maxMessageSize
        }
        return Pair(AMQPServer(
                "0.0.0.0",
                port,
                amqpConfig), nodeCert)
    }

    private fun Pair<CertificateStoreSupplier, TwoWaySslConfiguration>.recreateNodeCaAndTlsCertificates(nodeCaCrlDistPoint: String, tlsCrlDistPoint: String?): X509Certificate {

        val signingCertificateStore = first
        val p2pSslConfiguration = second
        val nodeKeyStore = signingCertificateStore.get()
        val (nodeCert, nodeKeys) = nodeKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA) }
        val newNodeCert = replaceCrlDistPointCaCertificate(nodeCert, CertificateType.NODE_CA, INTERMEDIATE_CA.keyPair, nodeCaCrlDistPoint)
        val nodeCertChain = listOf(newNodeCert, INTERMEDIATE_CA.certificate, *nodeKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_CA) }.drop(2).toTypedArray())
        nodeKeyStore.update {
            internal.deleteEntry(X509Utilities.CORDA_CLIENT_CA)
        }
        nodeKeyStore.update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_CA, nodeKeys.private, nodeCertChain)
        }
        val sslKeyStore = p2pSslConfiguration.keyStore.get()
        val (tlsCert, tlsKeys) = sslKeyStore.query { getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_TLS) }
        val newTlsCert = replaceCrlDistPointCaCertificate(tlsCert, CertificateType.TLS, nodeKeys, tlsCrlDistPoint, X500Name.getInstance(ROOT_CA.certificate.subjectX500Principal.encoded))
        val sslCertChain = listOf(newTlsCert, newNodeCert, INTERMEDIATE_CA.certificate, *sslKeyStore.query { getCertificateChain(X509Utilities.CORDA_CLIENT_TLS) }.drop(3).toTypedArray())

        sslKeyStore.update {
            internal.deleteEntry(X509Utilities.CORDA_CLIENT_TLS)
        }
        sslKeyStore.update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeys.private, sslCertChain)
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
        private val NODE_CRL = "node.crl"
        private val INTEMEDIATE_CRL = "intermediate.crl"
        private val EMPTY_CRL = "empty.crl"

        @GET
        @Path("node.crl")
        @Produces("application/pkcs7-crl")
        fun getNodeCRL(): Response {
            return Response.ok(createRevocationList(
                    INTERMEDIATE_CA.certificate,
                    INTERMEDIATE_CA.keyPair.private,
                    NODE_CRL,
                    false,
                    *revokedNodeCerts.toTypedArray()).encoded).build()
        }

        @GET
        @Path("intermediate.crl")
        @Produces("application/pkcs7-crl")
        fun getIntermediateCRL(): Response {
            return Response.ok(createRevocationList(
                    ROOT_CA.certificate,
                    ROOT_CA.keyPair.private,
                    INTEMEDIATE_CRL,
                    false,
                    *revokedIntermediateCerts.toTypedArray()).encoded).build()
        }

        @GET
        @Path("empty.crl")
        @Produces("application/pkcs7-crl")
        fun getEmptyCRL(): Response {
            return Response.ok(createRevocationList(
                    ROOT_CA.certificate,
                    ROOT_CA.keyPair.private,
                    EMPTY_CRL, true).encoded).build()
        }

        private fun createRevocationList(caCertificate: X509Certificate,
                                         caPrivateKey: PrivateKey,
                                         endpoint: String,
                                         indirect: Boolean,
                                         vararg serialNumbers: BigInteger): X509CRL {
            println("Generating CRL for $endpoint")
            val builder = JcaX509v2CRLBuilder(caCertificate.subjectX500Principal, Date(System.currentTimeMillis() - 1.minutes.toMillis()))
            val extensionUtils = JcaX509ExtensionUtils()
            builder.addExtension(Extension.authorityKeyIdentifier,
                    false, extensionUtils.createAuthorityKeyIdentifier(caCertificate))
            val issuingDistPointName = GeneralName(
                    GeneralName.uniformResourceIdentifier,
                    "http://${server.hostAndPort.host}:${server.hostAndPort.port}/crl/$endpoint")
            // This is required and needs to match the certificate settings with respect to being indirect
            val issuingDistPoint = IssuingDistributionPoint(DistributionPointName(GeneralNames(issuingDistPointName)), indirect, false)
            builder.addExtension(Extension.issuingDistributionPoint, true, issuingDistPoint)
            builder.setNextUpdate(Date(System.currentTimeMillis() + 1.seconds.toMillis()))
            serialNumbers.forEach {
                builder.addCRLEntry(it, Date(System.currentTimeMillis() - 10.minutes.toMillis()), ReasonFlags.certificateHold)
            }
            val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(caPrivateKey)
            return JcaX509CRLConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCRL(builder.build(signer))
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
}
