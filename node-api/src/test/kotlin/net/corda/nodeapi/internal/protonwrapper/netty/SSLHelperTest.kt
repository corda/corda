package net.corda.nodeapi.internal.protonwrapper.netty

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.testing.internal.configureTestSSL
import org.junit.Test
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.TrustManagerFactory
import kotlin.test.assertEquals

class SSLHelperTest {
    @Test
    fun `ensure SNI header in correct format`() {
        val legalName = CordaX500Name("Test", "London", "GB")
        val sslConfig = configureTestSSL(legalName)

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        val keyStore = sslConfig.keyStore
        keyManagerFactory.init(CertificateStore.fromFile(keyStore.path, keyStore.storePassword, keyStore.entryPassword, false))
        val trustStore = sslConfig.trustStore
        trustManagerFactory.init(initialiseTrustStoreAndEnableCrlChecking(CertificateStore.fromFile(trustStore.path, trustStore.storePassword, trustStore.entryPassword, false), false))

        val sslHandler = createClientSslHelper(NetworkHostAndPort("localhost", 1234), setOf(legalName), keyManagerFactory, trustManagerFactory)
        val legalNameHash = SecureHash.sha256(legalName.toString()).toString().take(32).toLowerCase()

        // These hardcoded values must not be changed, something is broken if you have to change these hardcoded values.
        assertEquals("O=Test, L=London, C=GB", legalName.toString())
        assertEquals("f3df3c01a5f5aa5b9d394680cde3a414", legalNameHash)
        assertEquals(1, sslHandler.engine().sslParameters.serverNames.size)
        assertEquals("$legalNameHash.corda.net", (sslHandler.engine().sslParameters.serverNames.first() as SNIHostName).asciiName)
    }
}