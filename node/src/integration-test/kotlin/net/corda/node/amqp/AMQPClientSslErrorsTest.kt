package net.corda.node.amqp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.div
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.initialiseTrustStoreAndEnableCrlChecking
import net.corda.nodeapi.internal.protonwrapper.netty.toRevocationConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

class AMQPClientSslErrorsTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val portAllocation = incrementalPortAllocation()

    private lateinit var serverKeyManagerFactory: KeyManagerFactory

    private lateinit var trustManagerFactory: TrustManagerFactory

    @Before
    fun setup() {

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
            override val maxMessageSize: Int = 10 * 1024
        }

        serverKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        serverKeyManagerFactory.init(serverAmqpConfig.keyStore.value.internal, serverAmqpConfig.keyStore.entryPassword.toCharArray())
        trustManagerFactory.init(initialiseTrustStoreAndEnableCrlChecking(serverAmqpConfig.trustStore, serverAmqpConfig.revocationConfig))
    }

    @Test(timeout = 300_000)
    fun trivialClientServerExchange() {
        val serverPort = portAllocation.nextPort()
        val serverRunnable = ServerRunnable(serverKeyManagerFactory, trustManagerFactory, serverPort)
        val serverThread = Thread(serverRunnable)
        serverThread.start()

        // System.setProperty("javax.net.debug", "all");

        val client = NioSslClient("TLSv1.2", "localhost", serverPort)
        client.connect()
        client.write("Hello! I am a client!")
        client.read()
        client.shutdown()

        val client2 = NioSslClient("TLSv1.2", "localhost", serverPort)
        val client3 = NioSslClient("TLSv1.2", "localhost", serverPort)
        val client4 = NioSslClient("TLSv1.2", "localhost", serverPort)

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

        serverRunnable.stop()
        serverThread.join()
    }
}