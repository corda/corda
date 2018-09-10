package net.corda.node.utilities

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.nodeapi.internal.crypto.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread
import kotlin.test.*

/**
 * Various tests for mixed-scheme mutual TLS authentication, such as:
 * Both TLS keys and CAs are using EC NIST P-256.
 * Both TLS keys and CAs are using RSA.
 * Server EC NIST P-256 - Client RSA.
 * Server RSA - Client EC NIST P-256.
 * Mixed CA and TLS keys.
 *
 * TLS/SSL protocols support a large number of cipher suites.
 * A cipher suite is a collection of symmetric and asymmetric encryption algorithms used by hosts to establish
 * a secure communication. Supported cipher suites can be classified based on encryption algorithm strength,
 * key length, key exchange and authentication mechanisms. Some cipher suites offer better level of security than others.
 *
 * Each TLS cipher suite has a unique name that is used to identify it and to describe the algorithmic contents of it.
 * Each segment in a cipher suite name stands for a different algorithm or protocol.
 * An example of a cipher suite name: TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
 * The meaning of this name is:
 * TLS defines the protocol that this cipher suite is for; it will usually be TLS.
 * ECDHE indicates the key exchange algorithm being used.
 * ECDSA indicates the authentication algorithm (signing the DH keys).
 * AES_128_GCM indicates the block cipher being used to encrypt the message stream.
 * SHA256 indicates the message authentication algorithm which is used to authenticate a message.
 */
class TLSAuthenticationTests {

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    // Root CA.
    private val ROOT_X500 = X500Principal("CN=Root_CA_1,O=R3CEV,L=London,C=GB")
    // Intermediate CA.
    private val INTERMEDIATE_X500 = X500Principal("CN=Intermediate_CA_1,O=R3CEV,L=London,C=GB")
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

    @Test
    fun `All EC R1`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1TLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2TLSScheme = Crypto.ECDSA_SECP256R1_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(serverSocketFactory, clientSocketFactory, 0, 0)

        testConnect(serverSocket, clientSocket, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    }

    @Test
    fun `All RSA`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.RSA_SHA256,
                intermediateCAScheme = Crypto.RSA_SHA256,
                client1CAScheme = Crypto.RSA_SHA256,
                client1TLSScheme = Crypto.RSA_SHA256,
                client2CAScheme = Crypto.RSA_SHA256,
                client2TLSScheme = Crypto.RSA_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(serverSocketFactory, clientSocketFactory, 0, 0)

        testConnect(serverSocket, clientSocket, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
    }

    // Server's public key type is the one selected if users use different key types (e.g RSA and EC R1).
    @Test
    fun `Server RSA - Client EC R1 - CAs all EC R1`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1TLSScheme = Crypto.RSA_SHA256,
                client2CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2TLSScheme = Crypto.ECDSA_SECP256R1_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(serverSocketFactory, clientSocketFactory, 0, 0)
        testConnect(serverSocket, clientSocket, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256") // Server's key type is selected.
    }

    @Test
    fun `Server EC R1 - Client RSA - CAs all EC R1`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1TLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2TLSScheme = Crypto.RSA_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(serverSocketFactory, clientSocketFactory, 0, 0)
        testConnect(serverSocket, clientSocket, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256") // Server's key type is selected.
    }

    @Test
    fun `Server EC R1 - Client EC R1 - CAs all RSA`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.RSA_SHA256,
                intermediateCAScheme = Crypto.RSA_SHA256,
                client1CAScheme = Crypto.RSA_SHA256,
                client1TLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2CAScheme = Crypto.RSA_SHA256,
                client2TLSScheme = Crypto.ECDSA_SECP256R1_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(serverSocketFactory, clientSocketFactory, 0, 0)
        testConnect(serverSocket, clientSocket, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    }

    @Test
    fun `Server EC R1 - Client RSA - Mixed CAs`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.RSA_SHA256,
                client1CAScheme = Crypto.RSA_SHA256,
                client1TLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2CAScheme = Crypto.ECDSA_SECP256K1_SHA256,
                client2TLSScheme = Crypto.RSA_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(serverSocketFactory, clientSocketFactory, 0, 0)
        testConnect(serverSocket, clientSocket, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    }

    @Test
    fun `All RSA - avoid ECC for DH`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.RSA_SHA256,
                intermediateCAScheme = Crypto.RSA_SHA256,
                client1CAScheme = Crypto.RSA_SHA256,
                client1TLSScheme = Crypto.RSA_SHA256,
                client2CAScheme = Crypto.RSA_SHA256,
                client2TLSScheme = Crypto.RSA_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(
                serverSocketFactory,
                clientSocketFactory,
                0,
                0,
                CORDA_TLS_CIPHER_SUITES,
                arrayOf("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")) // Second client accepts DHE only.
        testConnect(serverSocket, clientSocket, "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")
    }

    // According to RFC 5246 (TLS 1.2), section 7.4.1.2 ClientHello cipher_suites:
    // This is a list of the cryptographic options supported by the client, with the client's first preference first.
    //
    // However, the server is still free to ignore this order and pick what it thinks is best,
    // see https://security.stackexchange.com/questions/121608 for more information.
    @Test
    fun `TLS cipher suite order matters - client wins`() {
        val (serverSocketFactory, clientSocketFactory) = buildTLSFactories(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client1TLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2CAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                client2TLSScheme = Crypto.ECDSA_SECP256R1_SHA256
        )

        val (serverSocket, clientSocket) = buildTLSSockets(
                serverSocketFactory,
                clientSocketFactory,
                0,
                0,
                arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"), // GCM then CBC.
                arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")) // CBC then GCM.
        testConnect(serverSocket, clientSocket, "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256") // Client order wins.
    }

    private fun tempFile(name: String): Path = tempFolder.root.toPath() / name

    private fun buildTLSFactories(
            rootCAScheme: SignatureScheme,
            intermediateCAScheme: SignatureScheme,
            client1CAScheme: SignatureScheme,
            client1TLSScheme: SignatureScheme,
            client2CAScheme: SignatureScheme,
            client2TLSScheme: SignatureScheme
    ): Pair<SSLServerSocketFactory, SSLSocketFactory> {

        val trustStorePath = tempFile("cordaTrustStore.jks")
        val client1TLSKeyStorePath = tempFile("client1sslkeystore.jks")
        val client2TLSKeyStorePath = tempFile("client2sslkeystore.jks")

        // ROOT CA key and cert.
        val rootCAKeyPair = Crypto.generateKeyPair(rootCAScheme)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(ROOT_X500, rootCAKeyPair)

        // Intermediate CA key and cert.
        val intermediateCAKeyPair = Crypto.generateKeyPair(intermediateCAScheme)
        val intermediateCACert = X509Utilities.createCertificate(
                CertificateType.INTERMEDIATE_CA,
                rootCACert,
                rootCAKeyPair,
                INTERMEDIATE_X500,
                intermediateCAKeyPair.public
        )

        // Client 1 keys, certs and SSLKeyStore.
        val client1CAKeyPair = Crypto.generateKeyPair(client1CAScheme)
        val client1CACert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                intermediateCACert,
                intermediateCAKeyPair,
                CLIENT_1_X500.x500Principal,
                client1CAKeyPair.public
        )

        val client1TLSKeyPair = Crypto.generateKeyPair(client1TLSScheme)
        val client1TLSCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                client1CACert,
                client1CAKeyPair,
                CLIENT_1_X500.x500Principal,
                client1TLSKeyPair.public
        )

        val client1TLSKeyStore = loadOrCreateKeyStore(client1TLSKeyStorePath, PASSWORD)
        client1TLSKeyStore.addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                client1TLSKeyPair.private,
                PASSWORD.toCharArray(),
                arrayOf(client1TLSCert, client1CACert, intermediateCACert, rootCACert))
        // client1TLSKeyStore.save(client1TLSKeyStorePath, PASSWORD)

        // Client 2 keys, certs and SSLKeyStore.
        val client2CAKeyPair = Crypto.generateKeyPair(client2CAScheme)
        val client2CACert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                intermediateCACert,
                intermediateCAKeyPair,
                CLIENT_2_X500.x500Principal,
                client2CAKeyPair.public
        )

        val client2TLSKeyPair = Crypto.generateKeyPair(client2TLSScheme)
        val client2TLSCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                client2CACert,
                client2CAKeyPair,
                CLIENT_2_X500.x500Principal,
                client2TLSKeyPair.public
        )

        val client2TLSKeyStore = loadOrCreateKeyStore(client2TLSKeyStorePath, PASSWORD)
        client2TLSKeyStore.addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                client2TLSKeyPair.private,
                PASSWORD.toCharArray(),
                arrayOf(client2TLSCert, client2CACert, intermediateCACert, rootCACert))
        // client2TLSKeyStore.save(client2TLSKeyStorePath, PASSWORD)

        val trustStore = loadOrCreateKeyStore(trustStorePath, PASSWORD)
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCACert)
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_INTERMEDIATE_CA, intermediateCACert)
        // trustStore.save(trustStorePath, PASSWORD)

        val client1SSLContext = sslContext(client1TLSKeyStore, PASSWORD, trustStore)
        val client2SSLContext = sslContext(client2TLSKeyStore, PASSWORD, trustStore)

        val serverSocketFactory = client1SSLContext.serverSocketFactory
        val clientSocketFactory = client2SSLContext.socketFactory

        return Pair(serverSocketFactory, clientSocketFactory)
    }

    private fun buildTLSSockets(
            serverSocketFactory: SSLServerSocketFactory,
            clientSocketFactory: SSLSocketFactory,
            serverPort: Int = 0, // Use 0 to get first free socket.
            clientPort: Int = 0, // Use 0 to get first free socket.
            cipherSuitesServer: Array<String> = CORDA_TLS_CIPHER_SUITES,
            cipherSuitesClient: Array<String> = CORDA_TLS_CIPHER_SUITES
    ): Pair<SSLServerSocket, SSLSocket> {
        val serverSocket = serverSocketFactory.createServerSocket(serverPort) as SSLServerSocket // use 0 to get first free socket.
        val serverParams = SSLParameters(cipherSuitesServer, arrayOf("TLSv1.2"))
        serverParams.needClientAuth = true // Note that needClientAuth is requiring client authentication Vs wantClientAuth, in which client authentication is optional).
        serverParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        serverSocket.sslParameters = serverParams
        serverSocket.useClientMode = false

        val clientSocket = clientSocketFactory.createSocket() as SSLSocket
        val clientParams = SSLParameters(cipherSuitesClient, arrayOf("TLSv1.2"))
        clientParams.endpointIdentificationAlgorithm = null // Reconfirm default no server name indication, use our own validator.
        clientSocket.sslParameters = clientParams
        clientSocket.useClientMode = true
        // We need to specify this explicitly because by default the client binds to 'localhost' and we want it to bind
        // to whatever <hostname> resolves to(as that's what the server binds to). In particular on Debian <hostname>
        // resolves to 127.0.1.1 instead of the external address of the interface, so the TLS handshake fails.
        clientSocket.bind(InetSocketAddress(InetAddress.getLocalHost(), clientPort))
        return Pair(serverSocket, clientSocket)
    }

    private fun testConnect(serverSocket: ServerSocket, clientSocket: SSLSocket, expectedCipherSuite: String) {
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
        assertEquals(expectedCipherSuite, clientSocket.session.cipherSuite)

        // Timeout after 30 secs.
        val output = DataOutputStream(clientSocket.outputStream)
        output.writeUTF("Hello World")
        var timeout = 0
        synchronized(lock) {
            while (!done) {
                timeout++
                if (timeout > 30) throw IOException("Timed out waiting for server to complete")
                lock.wait(1000)
            }
        }

        clientSocket.close()
        serverThread.join(1000)
        assertFalse { serverError }
        serverSocket.close()
        assertTrue(done)
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