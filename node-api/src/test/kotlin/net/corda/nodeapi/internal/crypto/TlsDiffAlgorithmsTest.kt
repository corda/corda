package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.newSecureRandom
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.protonwrapper.netty.init
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class TlsDiffAlgorithmsTest(private val serverAlgo: String, private val clientAlgo: String) {
    companion object {
        val CIPHER_SUITES = arrayOf(
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        )

        @Parameterized.Parameters(name = "ServerAlgo: {0}, ClientAlgo: {1}")
        @JvmStatic
        fun data() = listOf(arrayOf("ec", "ec"), arrayOf("rsa", "rsa"), arrayOf("ec", "rsa"), arrayOf("rsa", "ec"))

        private val logger = contextLogger()
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testClientServerTlsExchange() {

        //System.setProperty("javax.net.debug", "all")

        logger.info("Testing: ServerAlgo: $serverAlgo, ClientAlgo: $clientAlgo")

        val trustStore = CertificateStore.fromResource("net/corda/nodeapi/internal/crypto/keystores/trust.jks", "trustpass", "trustpass")
        val rootCa = trustStore.value.getCertificate("root")

        val serverKeyStore = CertificateStore.fromResource("net/corda/nodeapi/internal/crypto/keystores/float_$serverAlgo.jks", "floatpass", "floatpass")
        val serverCa = serverKeyStore.value.getCertificateAndKeyPair("floatcert", "floatpass")

        val clientKeyStore = CertificateStore.fromResource("net/corda/nodeapi/internal/crypto/keystores/bridge_$clientAlgo.jks", "bridgepass", "bridgepass")
        //val clientCa = clientKeyStore.value.getCertificateAndKeyPair("bridgecert", "bridgepass")

        val serverSocketFactory = createSslContext(serverKeyStore, trustStore).serverSocketFactory
        val clientSocketFactory = createSslContext(clientKeyStore, trustStore).socketFactory

        val serverSocket = (serverSocketFactory.createServerSocket(0) as SSLServerSocket).apply {
            // use 0 to get first free socket
            val serverParams = SSLParameters(CIPHER_SUITES,
                    arrayOf("TLSv1.2"))
            serverParams.wantClientAuth = true
            serverParams.needClientAuth = true
            serverParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
            sslParameters = serverParams
            useClientMode = false
        }

        val clientSocket = (clientSocketFactory.createSocket() as SSLSocket).apply {
            val clientParams = SSLParameters(CIPHER_SUITES,
                    arrayOf("TLSv1.2"))
            clientParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
            sslParameters = clientParams
            useClientMode = true
            // We need to specify this explicitly because by default the client binds to 'localhost' and we want it to bind
            // to whatever <hostname> resolves to(as that's what the server binds to). In particular on Debian <hostname>
            // resolves to 127.0.1.1 instead of the external address of the interface, so the ssl handshake fails.
            bind(InetSocketAddress(InetAddress.getLocalHost(), 0))
        }

        val lock = Object()
        var done = false
        var serverError = false

        val testPhrase = "Hello World"
        val serverThread = thread {
            try {
                val sslServerSocket = serverSocket.accept()
                assertTrue(sslServerSocket.isConnected)
                val serverInput = DataInputStream(sslServerSocket.inputStream)
                val receivedString = serverInput.readUTF()
                assertEquals(testPhrase, receivedString)
                synchronized(lock) {
                    done = true
                    lock.notifyAll()
                }
                sslServerSocket.close()
            } catch (ex: Exception) {
                serverError = true
            }
        }

        clientSocket.connect(InetSocketAddress(InetAddress.getLocalHost(), serverSocket.localPort))
        assertTrue(clientSocket.isConnected)

        // Double check hostname manually
        val peerChain = clientSocket.session.peerCertificates.x509
        val peerX500Principal = peerChain[0].subjectX500Principal
        assertEquals(serverCa.certificate.subjectX500Principal, peerX500Principal)
        X509Utilities.validateCertificateChain(rootCa, peerChain)
        with(DataOutputStream(clientSocket.outputStream)) {
            writeUTF(testPhrase)
        }
        var timeout = 0
        synchronized(lock) {
            while (!done) {
                timeout++
                if (timeout > 10) throw IOException("Timed out waiting for server to complete")
                lock.wait(1000)
            }
        }

        clientSocket.close()
        serverThread.join(1000)
        assertFalse { serverError }
        serverSocket.close()
        assertTrue(done)
    }

    private fun createSslContext(keyStore: CertificateStore, trustStore: CertificateStore): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore)
            val keyManagers = keyManagerFactory.keyManagers
            val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustMgrFactory.init(trustStore)
            val trustManagers = trustMgrFactory.trustManagers
            init(keyManagers, trustManagers, newSecureRandom())
        }
    }
}