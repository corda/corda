package net.corda.node.utilities

import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.*
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.NettyTestClient
import net.corda.testing.internal.NettyTestHandler
import net.corda.testing.internal.NettyTestServer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.InetAddress
import java.nio.file.Path
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class NettyEngineBasedTlsAuthenticationTests(val sslSetup: SslSetup) {

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    // Root CA.
    private val ROOT_X500 = X500Principal("CN=Root_CA_1,O=R3CEV,L=London,C=GB")
    // Intermediate CA.
    private val INTERMEDIATE_X500 = X500Principal("CN=Intermediate_CA_1,O=R3CEV,L=London,C=GB")
    // TLS server (server).
    private val CLIENT_1_X500 = CordaX500Name(commonName = "Client_1", organisation = "R3CEV", locality = "London", country = "GB")
    // TLS client (client).
    private val CLIENT_2_X500 = CordaX500Name(commonName = "Client_2", organisation = "R3CEV", locality = "London", country = "GB")
    // Password for keys and keystores.
    private val PASSWORD = "dummypassword"
    // Default supported TLS schemes for Corda nodes.
    private val CORDA_TLS_CIPHER_SUITES = arrayOf(
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
    )

    private fun tempFile(name: String): Path = tempFolder.root.toPath() / name

    companion object {
        private val portAllocation = incrementalPortAllocation()

        data class SslSetup(val clientNative: Boolean, val serverNative: Boolean) {
            override fun toString(): String = "Client: ${if (clientNative) "openSsl" else "javaSsl"} Server: ${if (serverNative) "openSsl" else "javaSsl"} "
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<SslSetup> = listOf(
                SslSetup(false, false),
                SslSetup(true, false),
                SslSetup(false, true),
                SslSetup(true, true)
        )
    }

    @Test
    fun `All EC R1`() {
        val (serverContext, clientContext) = buildContexts(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverTLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientTLSScheme = Crypto.ECDSA_SECP256R1_SHA256
        )

        testConnect(serverContext, clientContext, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    }

    @Test
    fun `All RSA`() {
        val (serverContext, clientContext) = buildContexts(
                rootCAScheme = Crypto.RSA_SHA256,
                intermediateCAScheme = Crypto.RSA_SHA256,
                serverCAScheme = Crypto.RSA_SHA256,
                serverTLSScheme = Crypto.RSA_SHA256,
                clientCAScheme = Crypto.RSA_SHA256,
                clientTLSScheme = Crypto.RSA_SHA256
        )

        testConnect(serverContext, clientContext, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
    }

    // Server's public key type is the one selected if users use different key types (e.g RSA and EC R1).
    @Test
    fun `Server RSA - Client EC R1 - CAs all EC R1`() {
        val (serverContext, clientContext) = buildContexts(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverTLSScheme = Crypto.RSA_SHA256,
                clientCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientTLSScheme = Crypto.ECDSA_SECP256R1_SHA256
        )

        testConnect(serverContext, clientContext, "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256") // Server's key type is selected.
    }

    @Test
    fun `Server EC R1 - Client RSA - CAs all EC R1`() {
        val (serverContext, clientContext) = buildContexts(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverTLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientTLSScheme = Crypto.RSA_SHA256
        )

        testConnect(serverContext, clientContext, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256") // Server's key type is selected.
    }

    @Test
    fun `Server EC R1 - Client EC R1 - CAs all RSA`() {
        val (serverContext, clientContext) = buildContexts(
                rootCAScheme = Crypto.RSA_SHA256,
                intermediateCAScheme = Crypto.RSA_SHA256,
                serverCAScheme = Crypto.RSA_SHA256,
                serverTLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientCAScheme = Crypto.RSA_SHA256,
                clientTLSScheme = Crypto.ECDSA_SECP256R1_SHA256
        )

        testConnect(serverContext, clientContext, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    }

    @Test
    fun `Server EC R1 - Client RSA - Mixed CAs`() {
        val (serverContext, clientContext) = buildContexts(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.RSA_SHA256,
                serverCAScheme = Crypto.RSA_SHA256,
                serverTLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientTLSScheme = Crypto.RSA_SHA256
        )

        testConnect(serverContext, clientContext, "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
    }

    // According to RFC 5246 (TLS 1.2), section 7.4.1.2 ClientHello cipher_suites:
    // This is a list of the cryptographic options supported by the client, with the client's first preference first.
    //
    // However, the server is still free to ignore this order and pick what it thinks is best,
    // see https://security.stackexchange.com/questions/121608 for more information.
    @Test
    fun `TLS cipher suite order matters - implementation dependent`() {
        val (serverContext, clientContext) = buildContexts(
                rootCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                intermediateCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                serverTLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientCAScheme = Crypto.ECDSA_SECP256R1_SHA256,
                clientTLSScheme = Crypto.ECDSA_SECP256R1_SHA256,
                cipherSuitesServer = arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"), // GCM then CBC.
                cipherSuitesClient = arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256") // CBC then GCM.

        )

        val expectedCipherSuite = if (sslSetup.clientNative || sslSetup.serverNative)
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256" // server wins if boring ssl is involved
        else
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"  // client wins in pure JRE SSL
        testConnect(serverContext, clientContext, expectedCipherSuite)
    }

    private fun buildContexts(
            rootCAScheme: SignatureScheme,
            intermediateCAScheme: SignatureScheme,
            serverCAScheme: SignatureScheme,
            serverTLSScheme: SignatureScheme,
            clientCAScheme: SignatureScheme,
            clientTLSScheme: SignatureScheme,
            cipherSuitesServer: Array<String> = CORDA_TLS_CIPHER_SUITES,
            cipherSuitesClient: Array<String> = CORDA_TLS_CIPHER_SUITES
    ): Pair<SslContext, SslContext> {

        val trustStorePath = tempFile("cordaTrustStore.jks")
        val serverTLSKeyStorePath = tempFile("serversslkeystore.jks")
        val clientTLSKeyStorePath = tempFile("clientsslkeystore.jks")

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
        val serverCAKeyPair = Crypto.generateKeyPair(serverCAScheme)
        val serverCACert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                intermediateCACert,
                intermediateCAKeyPair,
                CLIENT_1_X500.x500Principal,
                serverCAKeyPair.public
        )

        val serverTLSKeyPair = Crypto.generateKeyPair(serverTLSScheme)
        val serverTLSCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                serverCACert,
                serverCAKeyPair,
                CLIENT_1_X500.x500Principal,
                serverTLSKeyPair.public
        )

        val serverTLSKeyStore = loadOrCreateKeyStore(serverTLSKeyStorePath, PASSWORD)
        serverTLSKeyStore.addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                serverTLSKeyPair.private,
                PASSWORD.toCharArray(),
                arrayOf(serverTLSCert, serverCACert, intermediateCACert, rootCACert))
        // serverTLSKeyStore.save(serverTLSKeyStorePath, PASSWORD)
        val serverTLSKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        serverTLSKeyManagerFactory.init(serverTLSKeyStore, PASSWORD.toCharArray())

        // Client 2 keys, certs and SSLKeyStore.
        val clientCAKeyPair = Crypto.generateKeyPair(clientCAScheme)
        val clientCACert = X509Utilities.createCertificate(
                CertificateType.NODE_CA,
                intermediateCACert,
                intermediateCAKeyPair,
                CLIENT_2_X500.x500Principal,
                clientCAKeyPair.public
        )

        val clientTLSKeyPair = Crypto.generateKeyPair(clientTLSScheme)
        val clientTLSCert = X509Utilities.createCertificate(
                CertificateType.TLS,
                clientCACert,
                clientCAKeyPair,
                CLIENT_2_X500.x500Principal,
                clientTLSKeyPair.public
        )

        val clientTLSKeyStore = loadOrCreateKeyStore(clientTLSKeyStorePath, PASSWORD)
        clientTLSKeyStore.addOrReplaceKey(
                X509Utilities.CORDA_CLIENT_TLS,
                clientTLSKeyPair.private,
                PASSWORD.toCharArray(),
                arrayOf(clientTLSCert, clientCACert, intermediateCACert, rootCACert))
        // clientTLSKeyStore.save(clientTLSKeyStorePath, PASSWORD)
        val clientTLSKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        clientTLSKeyManagerFactory.init(clientTLSKeyStore, PASSWORD.toCharArray())

        val trustStore = loadOrCreateKeyStore(trustStorePath, PASSWORD)
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCACert)
        trustStore.addOrReplaceCertificate(X509Utilities.CORDA_INTERMEDIATE_CA, intermediateCACert)
        // trustStore.save(trustStorePath, PASSWORD)
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(trustStore)

        return Pair(
                SslContextBuilder
                        .forServer(serverTLSKeyManagerFactory)
                        .trustManager(trustManagerFactory)
                        .ciphers(cipherSuitesServer.toMutableList())
                        .clientAuth(ClientAuth.REQUIRE)
                        .protocols("TLSv1.2")
                        .sslProvider(if (sslSetup.serverNative) SslProvider.OPENSSL else SslProvider.JDK)
                        .build(),
                SslContextBuilder
                        .forClient()
                        .keyManager(clientTLSKeyManagerFactory)
                        .trustManager(trustManagerFactory)
                        .ciphers(cipherSuitesClient.toMutableList())
                        .protocols("TLSv1.2")
                        .sslProvider(if (sslSetup.clientNative) SslProvider.OPENSSL else SslProvider.JDK)
                        .build()
        )
    }

    private fun testConnect(serverContext: SslContext, clientContext: SslContext, expectedCipherSuite: String) {
        val serverHandler = NettyTestHandler { ctx, msg -> ctx?.writeAndFlush(msg) }
        val clientHandler = NettyTestHandler { _, msg -> assertEquals("Hello!", NettyTestHandler.readString(msg)) }

        NettyTestServer(serverContext, serverHandler, portAllocation.nextPort()).use { server ->
            server.start()
            NettyTestClient(clientContext, InetAddress.getLocalHost().canonicalHostName, server.port, clientHandler).use { client ->
                client.start()

                clientHandler.writeString("Hello!")
                val readCalled = clientHandler.waitForReadCalled()
                clientHandler.rethrowIfFailed()
                serverHandler.rethrowIfFailed()
                assertEquals(1, serverHandler.readCalledCounter)
                assertEquals(1, clientHandler.readCalledCounter)
                assertTrue(readCalled)

                assertEquals(expectedCipherSuite, client.engine!!.session.cipherSuite)
            }
        }
    }
}