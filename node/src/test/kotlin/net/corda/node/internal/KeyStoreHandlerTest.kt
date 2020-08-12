package net.corda.node.internal

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.Crypto
import net.corda.core.internal.div
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.NODE_IDENTITY_KEY_ALIAS
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.testing.core.ALICE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeyStoreHandlerTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val certificateDir get() = tempFolder.root.toPath() / "certificates"

    private val config = mock<NodeConfiguration>()

    private val keyStoreHandler = KeyStoreHandler(config, mock())

    @Before
    fun before() {
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificateDir)
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir)

        p2pSslOptions.configureDevKeyAndTrustStores(ALICE_NAME, signingCertificateStore, certificateDir)

        whenever(config.devMode).thenReturn(false)
        whenever(config.signingCertificateStore).thenReturn(signingCertificateStore)
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)
        whenever(config.myLegalName).thenReturn(ALICE_NAME)
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with no node key store`() {
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificateDir,
                certificateStoreFileName = "invalid.jks")
        whenever(config.signingCertificateStore).thenReturn(signingCertificateStore)

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("One or more keyStores (identity or TLS) or trustStore not found.")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with no trust store`() {
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir, trustStoreFileName = "invalid.jks")
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("One or more keyStores (identity or TLS) or trustStore not found.")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with no TLS key store`() {
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir, keyStoreFileName = "invalid.jks")
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("One or more keyStores (identity or TLS) or trustStore not found.")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with invalid node key store password`() {
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificateDir, password = "invalid")
        whenever(config.signingCertificateStore).thenReturn(signingCertificateStore)

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("At least one of the keystores or truststore passwords does not match configuration")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with invalid trust store password`() {
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir, trustStorePassword = "invalid")
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("At least one of the keystores or truststore passwords does not match configuration")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with invalid TLS key store password`() {
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir, keyStorePassword = "invalid")
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("At least one of the keystores or truststore passwords does not match configuration")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode without trusted root`() {
        config.p2pSslOptions.trustStore.get().update {
            internal.deleteEntry(CORDA_ROOT_CA)
        }

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("Alias for trustRoot key not found. Please ensure you have an updated trustStore file")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode without alias for TLS key`() {
        config.p2pSslOptions.keyStore.get().update {
            internal.deleteEntry(CORDA_CLIENT_TLS)
        }

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("Alias for TLS key not found. Please ensure you have an updated TLS keyStore file")
    }

    @Test(timeout = 300_000)
    fun `initializing key store should throw exception if TLS certificate does not chain to the trust root`() {
        val keyPair  = Crypto.generateKeyPair()
        val tlsKeyPair  = Crypto.generateKeyPair()
        val untrustedRoot = X509Utilities.createSelfSignedCACertificate(ALICE_NAME.x500Principal, keyPair)
        val tlsCert = X509Utilities.createCertificate(CertificateType.TLS, untrustedRoot, keyPair, ALICE_NAME.x500Principal,
                tlsKeyPair.public)

        config.p2pSslOptions.keyStore.get().update {
            setPrivateKey(CORDA_CLIENT_TLS, tlsKeyPair.private, listOf(tlsCert, untrustedRoot), config.p2pSslOptions.keyStore.entryPassword)
        }

        assertThatThrownBy {
            keyStoreHandler.initKeyStores()
        }.hasMessageContaining("TLS certificate must chain to the trusted root")
    }

    @Test(timeout = 300_000)
    fun `initializing key store should return valid certificate if certificate is valid`() {
        val trustRoot = config.p2pSslOptions.trustStore.get()[CORDA_ROOT_CA]
        val certificate = keyStoreHandler.initKeyStores()

        assertThat(certificate).isEqualTo(listOf(trustRoot))
    }

    @Test(timeout = 300_000)
    fun `initializing key store in dev mode check keystore creation and crypto service`() {
        val devCertificateDir = tempFolder.root.toPath() / "certificates-dev"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(devCertificateDir)
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(devCertificateDir)
        val cryptoService = BCCryptoService(config.myLegalName.x500Principal, signingCertificateStore)

        whenever(config.devMode).thenReturn(true)
        whenever(config.signingCertificateStore).thenReturn(signingCertificateStore)
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)
        whenever(config.certificatesDirectory).thenReturn(devCertificateDir)

        assertThat(cryptoService.containsKey(NODE_IDENTITY_KEY_ALIAS)).isFalse()

        KeyStoreHandler(config, cryptoService).initKeyStores()

        assertThat(config.p2pSslOptions.trustStore.get().contains(CORDA_ROOT_CA)).isTrue()
        assertThat(config.p2pSslOptions.keyStore.get().contains(CORDA_CLIENT_TLS)).isTrue()
        assertThat(config.signingCertificateStore.get().contains(NODE_IDENTITY_KEY_ALIAS)).isTrue()
        assertThat(cryptoService.containsKey(NODE_IDENTITY_KEY_ALIAS)).isTrue()
    }
}
