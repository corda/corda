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

        keyManagerFactory.init(CertificateStore.fromFile(sslConfig.keyStore.path, sslConfig.keyStore.password, false))
        trustManagerFactory.init(initialiseTrustStoreAndEnableCrlChecking(CertificateStore.fromFile(sslConfig.trustStore.path, sslConfig.trustStore.password, false), false))

        val sslHandler = createClientSslHelper(NetworkHostAndPort("localhost", 1234), setOf(legalName), keyManagerFactory, trustManagerFactory)
        val legalNameHash = SecureHash.sha256(legalName.toString()).toString().take(32).toLowerCase()

        assertEquals(1, sslHandler.engine().sslParameters.serverNames.size)
        assertEquals("$legalNameHash.corda.net", (sslHandler.engine().sslParameters.serverNames.first() as SNIHostName).asciiName)
    }
}