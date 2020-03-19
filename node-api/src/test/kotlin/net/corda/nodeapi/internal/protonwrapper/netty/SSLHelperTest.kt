package net.corda.nodeapi.internal.protonwrapper.netty

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.coretesting.internal.configureTestSSL
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_PRIVATE_KEY_PASS
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import org.junit.Test
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.TrustManagerFactory
import kotlin.test.assertEquals

class SSLHelperTest {
    @Test(timeout=300_000)
	fun `ensure SNI header in correct format`() {
        val legalName = CordaX500Name("Test", "London", "GB")
        val sslConfig = configureTestSSL(legalName)

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        val keyStore = sslConfig.keyStore
        keyManagerFactory.init(CertificateStore.fromFile(keyStore.path, keyStore.storePassword, keyStore.entryPassword, false))
        val trustStore = sslConfig.trustStore
        trustManagerFactory.init(initialiseTrustStoreAndEnableCrlChecking(CertificateStore.fromFile(trustStore.path, trustStore.storePassword, trustStore.entryPassword, false), RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)))

        val sslHandler = createClientSslHelper(NetworkHostAndPort("localhost", 1234), setOf(legalName), keyManagerFactory, trustManagerFactory)
        val legalNameHash = SecureHash.sha256(legalName.toString()).toString().take(32).toLowerCase()

        // These hardcoded values must not be changed, something is broken if you have to change these hardcoded values.
        assertEquals("O=Test, L=London, C=GB", legalName.toString())
        assertEquals("f3df3c01a5f5aa5b9d394680cde3a414", legalNameHash)
        assertEquals(1, sslHandler.engine().sslParameters.serverNames.size)
        assertEquals("$legalNameHash.corda.net", (sslHandler.engine().sslParameters.serverNames.first() as SNIHostName).asciiName)
    }

    @Test(timeout=300_000)
	fun `test distributionPointsToString`() {
        val certStore = CertificateStore.fromResource(
                "net/corda/nodeapi/internal/protonwrapper/netty/sslkeystore_Revoked.jks",
                DEV_CA_KEY_STORE_PASS, DEV_CA_PRIVATE_KEY_PASS)
        val distPoints = certStore.query { getCertificateChain(CORDA_CLIENT_TLS).map { it.distributionPointsToString() } }
        assertEquals(listOf("NO CRLDP ext", "http://day-v3-doorman.cordaconnect.io/doorman",
                "http://day3-doorman.cordaconnect.io/doorman", "http://day3-doorman.cordaconnect.io/subordinate", "NO CRLDP ext"), distPoints)
    }
}