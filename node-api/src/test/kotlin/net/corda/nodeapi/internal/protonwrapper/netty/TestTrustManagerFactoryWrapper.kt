package net.corda.nodeapi.internal.protonwrapper.netty

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.div
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.net.ssl.TrustManagerFactory

class TestTrustManagerFactoryWrapper {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private abstract class AbstractNodeConfiguration : NodeConfiguration


    @Test
    fun testWrapping() {
        val baseDir = temporaryFolder.root.toPath() / "testWrapping"
        val certDir = baseDir / "certificates"
        val sslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(temporaryFolder.root.toPath(), keyStorePassword = "serverstorepass")
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(temporaryFolder.root.toPath())

        val config = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDir).whenever(it).baseDirectory
            doReturn(certDir).whenever(it).certificatesDirectory
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(sslConfig).whenever(it).p2pSslOptions
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
        }
        config.configureWithDevSSLCertificate()

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        val wrapped = LoggingTrustManagerFactoryWrapper(trustManagerFactory)
        wrapped.init(initialiseTrustStoreAndEnableCrlChecking(config.p2pSslOptions.trustStore.get(), RevocationConfigImpl(RevocationConfig.Mode.HARD_FAIL)))

        val trustManagers = wrapped.trustManagers
        assertTrue(trustManagers.isNotEmpty())
        assertTrue(trustManagers[0] is LoggingTrustManagerWrapper)
    }
}