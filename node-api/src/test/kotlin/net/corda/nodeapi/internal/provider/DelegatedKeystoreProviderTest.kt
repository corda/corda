package net.corda.nodeapi.internal.provider

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.Signature
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class DelegatedKeystoreProviderTest(private val serverSignatureScheme: SignatureScheme, private val clientSignatureScheme: SignatureScheme) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "signatureScheme = {0}, {1}")
        fun data() = listOf(arrayOf(Crypto.ECDSA_SECP256R1_SHA256,Crypto.ECDSA_SECP256R1_SHA256),
                arrayOf(Crypto.RSA_SHA256, Crypto.RSA_SHA256),
                arrayOf(Crypto.ECDSA_SECP256R1_SHA256, Crypto.RSA_SHA256))

        private const val PASSWORD = "password"
    }

    @Test
    fun `can establish TLS connection using remote signer`() {
        val CIPHER_SUITES = arrayOf(
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        )
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party

        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath(signatureScheme = serverSignatureScheme)
        val devNodeCa = createDevNodeCa(intermediateCa, MEGA_CORP.name, nodeKeyPair = Crypto.generateKeyPair(serverSignatureScheme))

        val trustStore = X509KeyStore(PASSWORD).apply { setCertificate(X509Utilities.CORDA_ROOT_CA, rootCa.certificate) }
        val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustMgrFactory.init(trustStore.internal)
        val trustManagers = trustMgrFactory.trustManagers

        val serverDelegatedKeyStore = X509KeyStore(PASSWORD).apply {
            val tlsKeyPair = Crypto.generateKeyPair(serverSignatureScheme)
            val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, devNodeCa.certificate, devNodeCa.keyPair, MEGA_CORP.name.x500Principal, tlsKeyPair.public)
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeyPair.private, listOf(tlsCert, devNodeCa.certificate, intermediateCa.certificate, rootCa.certificate), PASSWORD)
        }.delegated(trustStore)

        val clientDelegatedKeyStore = X509KeyStore(PASSWORD).apply {
            val tlsKeyPair = Crypto.generateKeyPair(clientSignatureScheme)
            val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, devNodeCa.certificate, devNodeCa.keyPair, MEGA_CORP.name.x500Principal, tlsKeyPair.public)
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeyPair.private, listOf(tlsCert, devNodeCa.certificate, intermediateCa.certificate, rootCa.certificate), PASSWORD)
        }.delegated(trustStore)


        val serverKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        serverKeyManagerFactory.init(serverDelegatedKeyStore.value.internal, PASSWORD.toCharArray())
        val serverKeyManagers = serverKeyManagerFactory.keyManagers
        val serverContext = SSLContext.getInstance("TLS")
        serverContext.init(serverKeyManagers, trustManagers, newSecureRandom())

        val clientKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        clientKeyManagerFactory.init(clientDelegatedKeyStore.value.internal, PASSWORD.toCharArray())
        val clientKeyManagers = clientKeyManagerFactory.keyManagers
        val clientContext = SSLContext.getInstance("TLS")
        clientContext.init(clientKeyManagers, trustManagers, newSecureRandom())

        val serverSocketFactory = serverContext.serverSocketFactory
        val clientSocketFactory = clientContext.socketFactory

        val serverSocket = serverSocketFactory.createServerSocket(0) as SSLServerSocket // use 0 to get first free socket
        val serverParams = SSLParameters(CIPHER_SUITES,
                arrayOf("TLSv1.2"))
        serverParams.wantClientAuth = false
        serverParams.needClientAuth = true
        serverParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        serverSocket.sslParameters = serverParams
        serverSocket.useClientMode = false

        val clientSocket = clientSocketFactory.createSocket() as SSLSocket
        val clientParams = SSLParameters(CIPHER_SUITES,
                arrayOf("TLSv1.2"))
        clientParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        clientSocket.sslParameters = clientParams
        clientSocket.useClientMode = true
        // We need to specify this explicitly because by default the client binds to 'localhost' and we want it to bind
        // to whatever <hostname> resolves to(as that's what the server binds to). In particular on Debian <hostname>
        // resolves to 127.0.1.1 instead of the external address of the interface, so the ssl handshake fails.
        clientSocket.bind(InetSocketAddress(InetAddress.getLocalHost(), 0))

        val lock = Object()
        var done = false
        var serverError = false

        val serverThread = thread {
            try {
                val sslServerSocket = serverSocket.accept()
                assertTrue(sslServerSocket.isConnected)
                val serverInput = DataInputStream(sslServerSocket.inputStream)
                val receivedString = serverInput.readUTF()
                assertEquals("Hello World", receivedString)
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
        assertEquals(MEGA_CORP.name.x500Principal, peerX500Principal)
        X509Utilities.validateCertificateChain(rootCa.certificate, peerChain)
        val output = DataOutputStream(clientSocket.outputStream)
        output.writeUTF("Hello World")
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

    private fun X509KeyStore.delegated(trustStore: X509KeyStore): CertificateStore {
        val localSigningService = object : DelegatedSigningService {
            override fun truststore(): CertificateStore = CertificateStore.of(trustStore, PASSWORD, PASSWORD)

            override fun sign(alias: String, signatureAlgorithm: String, data: ByteArray): ByteArray {
                val signature = Signature.getInstance(signatureAlgorithm)
                signature.initSign(getPrivateKey(alias, PASSWORD))
                signature.update(data)
                return signature.sign()
            }

            override fun certificates(): Map<String, List<X509Certificate>> = extractCertificates()
        }

        return localSigningService.keyStore()
    }
}