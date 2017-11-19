package net.corda.node.utilities

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.stream.Stream
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.test.*

class TLSAuthenticationTests {

    // Root CA.
    private val ROOT_X500 = CordaX500Name(commonName = "Root_CA_1", organisation = "R3CEV", locality = "London", country = "GB")
    // Intermediate CA.
    private val INTERMEDIATE_X500 = CordaX500Name(commonName = "Intermediate_CA_1", organisation = "R3CEV", locality = "London", country = "GB")
    // TLS server (client1).
    private val CLIENT_1_X500 = CordaX500Name(commonName = "Client_1", organisation = "R3CEV", locality = "London", country = "GB")
    // TLS client (client2).
    private val CLIENT_2_X500 = CordaX500Name(commonName = "Client_2", organisation = "R3CEV", locality = "London", country = "GB")
    // Password for keys and keystores.
    private val PASSWORD = "dummypassword"
    // Default supported TLS schemes for Corda nodes.
    private val CORDA_TLS_CIPHER_SUITES = arrayOf(
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
    )


    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `TLS test - RSA server key RSA - EC-R1 client key`() {
        // ROOT CA key and cert.
        val rootCAKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(ROOT_X500, rootCAKeyPair)

        // Intermediate CA key and cert.
        val intermediateCAKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val intermediateCACert = X509Utilities.createCertificate(
                CertificateType.INTERMEDIATE_CA,
                rootCACert,
                rootCAKeyPair,
                INTERMEDIATE_X500,
                intermediateCAKeyPair.public
        )

        // Client 1 keys, certs and SSLKeyStore.
        val client1CAKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)

        // val client1NameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, CLIENT_1_X500.x500Name))), arrayOf())
        val client1CACert = X509Utilities.createCertificate(
                CertificateType.CLIENT_CA,
                intermediateCACert,
                intermediateCAKeyPair,
                CLIENT_1_X500,
                client1CAKeyPair.public
        )

        val client1TLSKeyPair = Crypto.generateKeyPair(Crypto.RSA_SHA256)

        val client1TLSCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                client1CACert,
                client1CAKeyPair,
                CLIENT_1_X500,
                client1TLSKeyPair.public
        )

        val client1TLSKeyStorePath = tempFile("client1sslkeystore.jks")
        val client1TLSKeyStore = loadOrCreateKeyStore(client1TLSKeyStorePath, PASSWORD)
        client1TLSKeyStore.addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                client1TLSKeyPair.private,
                PASSWORD.toCharArray(),
                arrayOf(client1TLSCert, client1CACert, intermediateCACert, rootCACert))
        client1TLSKeyStore.save(client1TLSKeyStorePath, PASSWORD)


        // Client 2 keys, certs and SSLKeyStore.
        val client2CAKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)

        val client2CACert = X509Utilities.createCertificate(
                CertificateType.CLIENT_CA,
                intermediateCACert,
                intermediateCAKeyPair,
                CLIENT_2_X500,
                client2CAKeyPair.public
        )

        val client2TLSKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val client2TLSCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                client2CACert,
                client2CAKeyPair,
                CLIENT_2_X500,
                client2TLSKeyPair.public
        )

        val client2TLSKeyStorePath = tempFile("client2sslkeystore.jks")
        val client2TLSKeyStore = loadOrCreateKeyStore(client2TLSKeyStorePath, PASSWORD)
        client2TLSKeyStore.addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                client2TLSKeyPair.private,
                PASSWORD.toCharArray(),
                arrayOf(client2TLSCert, client2CACert, intermediateCACert, rootCACert))
        client2TLSKeyStore.save(client2TLSKeyStorePath, PASSWORD)

        val trustStorePath = tempFile("cordaTrustStore.jks")
        val trustStore = loadOrCreateKeyStore(trustStorePath, PASSWORD)

        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCACert.cert)
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_INTERMEDIATE_CA, intermediateCACert.cert)

        trustStore.save(trustStorePath, PASSWORD)


        val client1SSLContext = sslContext(client1TLSKeyStore, PASSWORD, trustStore)
        val client2SSLContext = sslContext(client2TLSKeyStore, PASSWORD, trustStore)

        val serverSocketFactory = client1SSLContext.serverSocketFactory
        val clientSocketFactory = client2SSLContext.socketFactory

        val serverSocket = serverSocketFactory.createServerSocket(0) as SSLServerSocket // use 0 to get first free socket.
        val serverParams = SSLParameters(CORDA_TLS_CIPHER_SUITES, arrayOf("TLSv1.2"))
        serverParams.needClientAuth = true
        serverParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        serverSocket.sslParameters = serverParams
        serverSocket.useClientMode = false

        val clientSocket = clientSocketFactory.createSocket() as SSLSocket
        val clientParams = SSLParameters(CORDA_TLS_CIPHER_SUITES, arrayOf("TLSv1.2"))
        clientParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        clientSocket.sslParameters = clientParams
        clientSocket.useClientMode = true
        // We need to specify this explicitly because by default the client binds to 'localhost' and we want it to bind
        // to whatever <hostname> resolves to(as that's what the server binds to). In particular on Debian <hostname>
        // resolves to 127.0.1.1 instead of the external address of the interface, so the TLS handshake fails.
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
            } catch (ex: Throwable) {
                serverError = true
            }
        }

        clientSocket.connect(InetSocketAddress(InetAddress.getLocalHost(), serverSocket.localPort))
        assertTrue(clientSocket.isConnected)

        // Server (client1) uses RSA and client2 EC key. So we expect they agree on TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256.
        assertEquals("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", clientSocket.session.cipherSuite)

        // Double check hostname manually.
        val peerChain = clientSocket.session.peerCertificates
        val peerX500Principal = (peerChain[0] as X509Certificate).subjectX500Principal
        assertEquals(CLIENT_1_X500.x500Principal, peerX500Principal)
        X509Utilities.validateCertificateChain(trustStore.getX509Certificate(X509Utilities.CORDA_ROOT_CA), *peerChain)
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

    private fun tempFile(name: String): Path = tempFolder.root.toPath() / name

    data class KeyStoreAndTrustStore (val keystore: KeyStore, val trustStore: KeyStore)

    /**
     * All in one wrapper to manufacture a root CA cert and an Intermediate CA cert.
     * Normally this would be run once and then the outputs would be re-used repeatedly to manufacture the server certs
     * @param keyStoreFilePath The output KeyStore path to publish the private keys of the CA root and intermediate certs into.
     * @param keyStorePassword The storage password to protect access to the generated KeyStore and public certificates
     * @param keyPassword The password that protects the CA private keys.
     * Unlike the SSL libraries that tend to assume the password is the same as the keystore password.
     * These CA private keys should be protected more effectively with a distinct password.
     * @param trustStoreFilePath The output KeyStore to place the Root CA public certificate, which can be used as an SSL truststore
     * @param trustStorePassword The password to protect the truststore
     * @return The KeyStore object that was saved to file
     */
    private fun createCAKeyStoreAndTrustStore(
            keyStoreFilePath: Path,
            keyStorePassword: String,
            keyPassword: String,
            trustStoreFilePath: Path,
            trustStorePassword: String,
            caSignatureScheme: SignatureScheme = X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME,
            intermediateSignatureSheme: SignatureScheme = X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME,
            caX500: CordaX500Name = ROOT_X500,
            intermediateCaX500: CordaX500Name = INTERMEDIATE_X500
    ): KeyStoreAndTrustStore {
        val rootCAKey = Crypto.generateKeyPair(caSignatureScheme)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(caX500, rootCAKey)

        val intermediateCAKeyPair = Crypto.generateKeyPair(intermediateSignatureSheme)
        val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey, intermediateCaX500, intermediateCAKeyPair.public)

        val keyPass = keyPassword.toCharArray()
        val keyStore = loadOrCreateKeyStore(keyStoreFilePath, keyStorePassword)

        keyStore.addOrReplaceKey(X509Utilities.CORDA_ROOT_CA, rootCAKey.private, keyPass, arrayOf<Certificate>(rootCACert.cert))

        keyStore.addOrReplaceKey(X509Utilities.CORDA_INTERMEDIATE_CA,
                intermediateCAKeyPair.private,
                keyPass,
                Stream.of(intermediateCACert, rootCACert).map { it.cert }.toTypedArray<Certificate>())

        keyStore.save(keyStoreFilePath, keyStorePassword)

        val trustStore = loadOrCreateKeyStore(trustStoreFilePath, trustStorePassword)

        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCACert.cert)
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_INTERMEDIATE_CA, intermediateCACert.cert)

        trustStore.save(trustStoreFilePath, trustStorePassword)

        return KeyStoreAndTrustStore(keyStore, trustStore)
    }

    // Generate an SSLContext from a KeyStore and a TrustStore.
    private fun sslContext(sslKeyStore: KeyStore, sslKeyStorePassword: String, sslTrustStore: KeyStore) : SSLContext  {
        val context = SSLContext.getInstance("TLS")
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        // Requires the KeyStore password as well.
        keyManagerFactory.init(sslKeyStore, sslKeyStorePassword.toCharArray())
        val keyManagers = keyManagerFactory.keyManagers
        val trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        // Password is not required for TrustStore.
        trustMgrFactory.init(sslTrustStore)
        val trustManagers = trustMgrFactory.trustManagers
        return context.apply {  init(keyManagers, trustManagers, newSecureRandom()) }
    }
}