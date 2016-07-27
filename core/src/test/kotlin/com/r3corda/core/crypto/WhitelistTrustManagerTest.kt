package com.r3corda.core.crypto

import org.junit.BeforeClass
import org.junit.Test
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WhitelistTrustManagerTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTrustManager() {
            // Validate original factory
            assertEquals("PKIX", TrustManagerFactory.getDefaultAlgorithm())

            //register for all tests
            registerWhitelistTrustManager()
        }
    }

    private fun getTrustmanagerAndCert(whitelist: String, certificateName: String): Pair<X509ExtendedTrustManager, X509Certificate> {
        WhitelistTrustManagerProvider.addWhitelistEntry(whitelist)

        val caCertAndKey = X509Utilities.createSelfSignedCACert(certificateName)

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("cacert", caCertAndKey.certificate)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        return Pair(trustManagerFactory.trustManagers.first() as X509ExtendedTrustManager, caCertAndKey.certificate)
    }

    private fun getTrustmanagerAndUntrustedChainCert(): Pair<X509ExtendedTrustManager, X509Certificate> {
        WhitelistTrustManagerProvider.addWhitelistEntry("test.r3corda.com")

        val otherCaCertAndKey = X509Utilities.createSelfSignedCACert("bad root")

        val caCertAndKey = X509Utilities.createSelfSignedCACert("good root")

        val subject = X509Utilities.getDevX509Name("test.r3corda.com")
        val serverKey = X509Utilities.generateECDSAKeyPairForSSL()
        val serverCert = X509Utilities.createServerCert(subject,
                serverKey.public,
                otherCaCertAndKey,
                listOf(),
                listOf())

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("cacert", caCertAndKey.certificate)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        return Pair(trustManagerFactory.trustManagers.first() as X509ExtendedTrustManager, serverCert)
    }


    @Test
    fun `getDefaultAlgorithm TrustManager is WhitelistTrustManager`() {
        registerWhitelistTrustManager() // Check double register is safe

        assertEquals("whitelistTrustManager", TrustManagerFactory.getDefaultAlgorithm())

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        trustManagerFactory.init(null as KeyStore?)

        val trustManagers = trustManagerFactory.trustManagers

        assertTrue { trustManagers.all { it is WhitelistTrustManager } }
    }

    @Test
    fun `check certificate works for whitelisted certificate and specific domain`() {
        val (trustManager, cert) = getTrustmanagerAndCert("test.r3corda.com", "test.r3corda.com")

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM)

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?)

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?)
    }

    @Test
    fun `check certificate works for specific certificate and wildcard permitted domain`() {
        val (trustManager, cert) = getTrustmanagerAndCert("*.r3corda.com", "test.r3corda.com")

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM)

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?)

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?)
    }

    @Test
    fun `check certificate works for wildcard certificate and non wildcard domain`() {
        val (trustManager, cert) = getTrustmanagerAndCert("*.r3corda.com", "test.r3corda.com")

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM)

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?)

        trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?)

        trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?)
    }

    @Test
    fun `check unknown certificate rejected`() {
        val (trustManager, cert) = getTrustmanagerAndCert("test.r3corda.com", "test.notr3.com")

        assertFailsWith<CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }

        assertFailsWith<CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }
    }

    @Test
    fun `check unknown wildcard certificate rejected`() {
        val (trustManager, cert) = getTrustmanagerAndCert("test.r3corda.com", "*.notr3.com")

        assertFailsWith<CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }

        assertFailsWith<CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }
    }

    @Test
    fun `check unknown certificate rejected against mismatched wildcard`() {
        val (trustManager, cert) = getTrustmanagerAndCert("*.r3corda.com", "test.notr3.com")

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }
    }

    @Test
    fun `check certificate signed by untrusted root is still rejected, despite matched name`() {
        val (trustManager, cert) = getTrustmanagerAndUntrustedChainCert()

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkServerTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as Socket?) }

        assertFailsWith<java.security.cert.CertificateException> { trustManager.checkClientTrusted(arrayOf(cert), X509Utilities.SIGNATURE_ALGORITHM, null as SSLEngine?) }
    }
}