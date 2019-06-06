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
import net.corda.nodeapi.internal.amqp.crl.CrlServer
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.EMPTY_CRL
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.FORBIDDEN_CRL
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.INTERMEDIATE_CRL
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.NODE_CRL
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.SIGNATURE_ALGORITHM
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.createRevocationList
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.recreateNodeCaAndTlsCertificates
import net.corda.nodeapi.internal.amqp.crl.CrlServlet.Companion.replaceCrlDistPointCaCertificate
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.*
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.DEV_INTERMEDIATE_CA
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigInteger
import java.security.Security
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CertificateRevocationListNodeTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val ROOT_CA = DEV_ROOT_CA
    private lateinit var INTERMEDIATE_CA: CertificateAndKeyPair

    private val portAllocation = incrementalPortAllocation(10000)
    private val serverPort = portAllocation.nextPort()

    private lateinit var server: CrlServer

    private val crlServerHitCount = AtomicInteger(0)

    private val revokedNodeCerts: MutableSet<BigInteger> = mutableSetOf()
    private val revokedIntermediateCerts: MutableSet<BigInteger> = mutableSetOf()

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Before
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        revokedNodeCerts.clear()
        server = CrlServer(NetworkHostAndPort("localhost", 0), crlServerHitCount, ROOT_CA, { INTERMEDIATE_CA }, revokedNodeCerts, revokedIntermediateCerts)
        server.start()
        INTERMEDIATE_CA = CertificateAndKeyPair(replaceCrlDistPointCaCertificate(
                DEV_INTERMEDIATE_CA.certificate,
                CertificateType.INTERMEDIATE_CA,
                ROOT_CA.keyPair,
                "http://${server.hostAndPort}/crl/$INTERMEDIATE_CRL"), DEV_INTERMEDIATE_CA.keyPair)
        crlServerHitCount.set(0)
    }

    @After
    fun tearDown() {
        server.close()
        revokedNodeCerts.clear()
        revokedIntermediateCerts.clear()
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
        assertTrue(crlServerHitCount.get() > 0)
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
    fun `Revocation status check fails when the CRL distribution point is not set and soft fail is disabled`() {
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
    fun `Revocation status check succeds when the CRL distribution point is not set and soft fail is enabled`() {
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
                             nodeCrlDistPoint: String = "http://${server.hostAndPort}/crl/$NODE_CRL",
                             tlsCrlDistPoint: String? = "http://${server.hostAndPort}/crl/$EMPTY_CRL",
                             maxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<AMQPClient, X509Certificate> {

        return createClient(targetPort, crlCheckSoftFail.toRevocationConfig(), nodeCrlDistPoint, tlsCrlDistPoint, maxMessageSize)
    }

    private fun createClient(targetPort: Int,
                             revocationConfig: RevocationConfig,
                             nodeCrlDistPoint: String = "http://${server.hostAndPort}/crl/$NODE_CRL",
                             tlsCrlDistPoint: String? = "http://${server.hostAndPort}/crl/$EMPTY_CRL",
                             maxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<AMQPClient, X509Certificate> {
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
        }
        clientConfig.configureWithDevSSLCertificate()
        val nodeCert = (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, tlsCrlDistPoint, ROOT_CA, INTERMEDIATE_CA)
        val keyStore = clientConfig.p2pSslOptions.keyStore.get()

        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = clientConfig.p2pSslOptions.trustStore.get()
            override val revocationConfig = revocationConfig
            override val maxMessageSize: Int = maxMessageSize
        }
        return Pair(AMQPClient(
                listOf(NetworkHostAndPort("localhost", targetPort)),
                setOf(ALICE_NAME, CHARLIE_NAME),
                amqpConfig), nodeCert)
    }

    private fun createServer(port: Int, name: CordaX500Name = ALICE_NAME,
                             crlCheckSoftFail: Boolean,
                             nodeCrlDistPoint: String = "http://${server.hostAndPort}/crl/$NODE_CRL",
                             tlsCrlDistPoint: String? = "http://${server.hostAndPort}/crl/$EMPTY_CRL",
                             maxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<AMQPServer, X509Certificate> {
        return createServer(port, name, crlCheckSoftFail.toRevocationConfig(), nodeCrlDistPoint, tlsCrlDistPoint, maxMessageSize)
    }

    private fun createServer(port: Int, name: CordaX500Name = ALICE_NAME,
                             revocationConfig: RevocationConfig,
                             nodeCrlDistPoint: String = "http://${server.hostAndPort}/crl/$NODE_CRL",
                             tlsCrlDistPoint: String? = "http://${server.hostAndPort}/crl/$EMPTY_CRL",
                             maxMessageSize: Int = MAX_MESSAGE_SIZE): Pair<AMQPServer, X509Certificate> {
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
        val nodeCert = (signingCertificateStore to p2pSslConfiguration).recreateNodeCaAndTlsCertificates(nodeCrlDistPoint, tlsCrlDistPoint, ROOT_CA, INTERMEDIATE_CA)
        val keyStore = serverConfig.p2pSslOptions.keyStore.get()
        val amqpConfig = object : AMQPConfiguration {
            override val keyStore = keyStore
            override val trustStore = serverConfig.p2pSslOptions.trustStore.get()
            override val revocationConfig = revocationConfig
            override val maxMessageSize: Int = maxMessageSize
        }
        return Pair(AMQPServer(
                "0.0.0.0",
                port,
                amqpConfig), nodeCert)
    }

    @Test
    fun `verify CRL algorithms`() {
        val ECDSA_ALGORITHM = "SHA256withECDSA"
        val EC_ALGORITHM = "EC"

        val crl = createRevocationList(
                server,
                ECDSA_ALGORITHM,
                ROOT_CA.certificate,
                ROOT_CA.keyPair.private,
                EMPTY_CRL,
                true,
                emptySet())
        // This should pass.
        crl.verify(ROOT_CA.keyPair.public)

        // Try changing the algorithm to EC will fail.
        assertThatIllegalArgumentException().isThrownBy {
            createRevocationList(
                    server,
                    EC_ALGORITHM,
                    ROOT_CA.certificate,
                    ROOT_CA.keyPair.private,
                    EMPTY_CRL,
                    true,
                    emptySet()
            )
        }.withMessage("Unknown signature type requested: EC")
    }

    @Test
    fun `AMPQ Client to Server connection works when client certificate is revoked and CRL check is OFF`() {

        val revocationConfig = RevocationConfigImpl(RevocationConfig.Mode.OFF)

        val (amqpServer, _) = createServer(serverPort, revocationConfig = revocationConfig)
        amqpServer.use {
            amqpServer.start()
            val checkPerformed = AtomicBoolean(false)
            val receiveSubs = amqpServer.onReceive.subscribe {
                assertEquals(BOB_NAME.toString(), it.sourceLegalName)
                assertEquals(P2P_PREFIX + "Test", it.topic)
                assertEquals("Test", String(it.payload))
                it.complete(true)
                checkPerformed.set(true)
            }
            val (amqpClient, clientCert) = createClient(serverPort, revocationConfig = revocationConfig)
            revokedNodeCerts.add(clientCert.serialNumber)
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
                assertTrue(checkPerformed.get())
                receiveSubs.unsubscribe()
            }
        }
        assertEquals(0, crlServerHitCount.get())
    }

    @Test
    fun `AMPQ Client to Server connection rejected when client certificate is revoked and external CRL source is used`() {

        val revocationConfig = RevocationConfigImpl(RevocationConfig.Mode.EXTERNAL_SOURCE,
            object : ExternalCrlSource {
                override fun fetch(certificate: X509Certificate): Set<X509CRL> {
                    return setOf(
                            createRevocationList(
                            server,
                            SIGNATURE_ALGORITHM,
                            INTERMEDIATE_CA.certificate,
                            INTERMEDIATE_CA.keyPair.private,
                            NODE_CRL,
                            false,
                            revokedNodeCerts))
                }
            }
        )

        val (amqpServer, _) = createServer(serverPort, revocationConfig = revocationConfig)
        amqpServer.use {
            amqpServer.start()
            val checkPerformed = AtomicBoolean(false)
            val receiveSubs = amqpServer.onReceive.subscribe {
                checkPerformed.set(true)
            }
            val (amqpClient, clientCert) = createClient(serverPort, revocationConfig = revocationConfig)
            revokedNodeCerts.add(clientCert.serialNumber)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertFalse(serverConnect.connected)
                assertTrue(serverConnect.badCert)
                val clientConnect = clientConnected.get()
                assertFalse(clientConnect.connected)
                assertTrue(clientConnect.badCert)
                assertFalse(checkPerformed.get())
                receiveSubs.unsubscribe()
            }
        }
        assertEquals(0, crlServerHitCount.get())
    }

    @Test
    fun `AMPQ Client to Server connection succeeds when CRL retrieval is forbidden and soft fail is enabled`() {
        val crlCheckSoftFail = true
        val forbiddenUrl = "http://${server.hostAndPort}/crl/$FORBIDDEN_CRL"
        val (amqpServer, _) = createServer(
                serverPort,
                crlCheckSoftFail = crlCheckSoftFail,
                nodeCrlDistPoint = forbiddenUrl,
                tlsCrlDistPoint = forbiddenUrl)
        amqpServer.use {
            amqpServer.start()
            amqpServer.onReceive.subscribe {
                it.complete(true)
            }
            val (amqpClient, _) = createClient(
                    serverPort,
                    crlCheckSoftFail,
                    nodeCrlDistPoint = forbiddenUrl,
                    tlsCrlDistPoint = forbiddenUrl)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
            }
        }
    }
}