package net.corda.nodeapi.internal.protonwrapper.netty

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.div
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509KeyManager
import kotlin.test.*

class TestKeyManagerFactoryWrapper {

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

        val underlyingKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

        val wrappedKeyManagerFactory = CertHoldingKeyManagerFactoryWrapper(underlyingKeyManagerFactory)
        wrappedKeyManagerFactory.init(config.p2pSslOptions.keyStore.get())
        val keyManagers = wrappedKeyManagerFactory.keyManagers
        assertFalse(keyManagers.isEmpty())
        assertNull(wrappedKeyManagerFactory.getCurrentCertChain())
        val keyManager = keyManagers.first() as X509KeyManager
        val alias = keyManager.chooseClientAlias(arrayOf("EC_EC"), null, null)
        assertNotNull(alias)
        val certChain = wrappedKeyManagerFactory.getCurrentCertChain()
        assertNotNull(certChain)
        assertTrue(certChain!!.isNotEmpty())

        assertEquals(alias, (keyManager as AliasProvidingKeyMangerWrapper).lastAlias)
    }

    @Test
    fun testWrappingSeparately() {
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

        val underlyingKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

        val wrappedKeyManagerFactory = CertHoldingKeyManagerFactoryWrapper(underlyingKeyManagerFactory)
        wrappedKeyManagerFactory.init(config.p2pSslOptions.keyStore.get())

        val otherWrappedKeyManagerFactory = CertHoldingKeyManagerFactoryWrapper(underlyingKeyManagerFactory)

        val keyManagers = wrappedKeyManagerFactory.keyManagers
        assertFalse(keyManagers.isEmpty())
        assertNull(wrappedKeyManagerFactory.getCurrentCertChain())
        val keyManager = keyManagers.first() as X509KeyManager
        keyManager.chooseClientAlias(arrayOf("EC_EC"), null, null)
        val certChain = wrappedKeyManagerFactory.getCurrentCertChain()
        assertNotNull(certChain)
        assertTrue(certChain!!.isNotEmpty())

        assertNull(otherWrappedKeyManagerFactory.getCurrentCertChain())
    }

}