package net.corda.node.internal

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.testing.core.ALICE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.IOException
import java.security.KeyStoreException
import java.security.cert.X509Certificate

class NodeKeyStoreUtilitiesTest {
    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with no key store`() {
        whenever(signingSupplier.get()).doAnswer { throw IOException() }

        assertThatThrownBy {
            config.initKeyStores(cryptoService)
        }.hasMessageContaining("One or more keyStores (identity or TLS) or trustStore not found.")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode with invalid password`() {
        whenever(signingSupplier.get()).doAnswer { throw KeyStoreException() }

        assertThatThrownBy {
            config.initKeyStores(cryptoService)
        }.hasMessageContaining("At least one of the keystores or truststore passwords does not match configuration")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode without trusted root`() {
        whenever(trustStore.contains(CORDA_ROOT_CA)).thenReturn(false)

        assertThatThrownBy {
            config.initKeyStores(cryptoService)
        }.hasMessageContaining("Alias for trustRoot key not found. Please ensure you have an updated trustStore file")
    }

    @Test(timeout = 300_000)
    fun `initializing key store in non-dev mode without alias for TLS key`() {
        whenever(keyStore.contains(CORDA_CLIENT_TLS)).thenReturn(false)

        assertThatThrownBy {
            config.initKeyStores(cryptoService)
        }.hasMessageContaining("Alias for TLS key not found. Please ensure you have an updated TLS keyStore file")
    }

    @Test(timeout = 300_000)
    fun `initializing key store should throw exception if TLS certificate does not chain to the trust root`() {
        val untrustedRoot = mock<X509Certificate>()
        whenever(keyStore.query(any<X509KeyStore.() -> List<X509Certificate>>())).thenReturn(mutableListOf(untrustedRoot))

        assertThatThrownBy {
            config.initKeyStores(cryptoService)
        }.hasMessageContaining("TLS certificate must chain to the trusted root")
    }

    @Test(timeout = 300_000)
    fun `initializing key store should return valid certificate if certificate is valid`() {
        val certificate = config.initKeyStores(cryptoService)

        assertThat(certificate).isEqualTo(trustRoot)
    }

    @Test(timeout = 300_000)
    fun `initializing key store in dev mode check te supplier`() {
        whenever(config.devMode).thenReturn(true)
        whenever(config.myLegalName).thenReturn(ALICE_NAME)
        whenever(config.certificatesDirectory).thenReturn(mock())
        whenever(trustSupplier.getOptional()).thenReturn(mock())
        whenever(keySupplier.getOptional()).thenReturn(mock())
        whenever(signingSupplier.getOptional()).thenReturn(mock())

        config.initKeyStores(cryptoService)

        verify(signingSupplier).getOptional()
    }

    @Test(timeout = 300_000)
    fun `initializing key store in dev mode with BCCryptoService call resyncKeystore`() {
        val bCryptoService = mock<BCCryptoService>()
        whenever(config.devMode).thenReturn(true)
        whenever(config.myLegalName).thenReturn(ALICE_NAME)
        whenever(config.certificatesDirectory).thenReturn(mock())
        whenever(trustSupplier.getOptional()).thenReturn(mock())
        whenever(keySupplier.getOptional()).thenReturn(mock())
        whenever(signingSupplier.getOptional()).thenReturn(mock())

        config.initKeyStores(bCryptoService)

        verify(bCryptoService).resyncKeystore()
    }

    private val config = mock<NodeConfiguration>()

    private val trustStore = mock<CertificateStore>()
    private val signingStore = mock<CertificateStore>()
    private val keyStore = mock<CertificateStore>()
    private val sslOptions = mock<MutualSslConfiguration>()
    private val trustSupplier = mock<FileBasedCertificateStoreSupplier>()
    private val signingSupplier = mock<FileBasedCertificateStoreSupplier>()
    private val keySupplier = mock<FileBasedCertificateStoreSupplier>()
    private val trustRoot = mock<X509Certificate>()
    private val cryptoService = mock<CryptoService>()

    init {
        whenever(config.devMode).thenReturn(false)

        whenever(sslOptions.keyStore).thenReturn(keySupplier)
        whenever(sslOptions.trustStore).thenReturn(trustSupplier)
        whenever(config.signingCertificateStore).thenReturn(signingSupplier)
        whenever(trustSupplier.get()).thenReturn(trustStore)
        whenever(signingSupplier.get()).thenReturn(signingStore)
        whenever(keySupplier.get()).thenReturn(keyStore)
        whenever(trustStore.contains(CORDA_ROOT_CA)).thenReturn(true)
        whenever(keyStore.contains(CORDA_CLIENT_TLS)).thenReturn(true)
        whenever(signingStore.contains(CORDA_CLIENT_CA)).thenReturn(true)
        whenever(config.p2pSslOptions).thenReturn(sslOptions)
        whenever(trustStore[CORDA_ROOT_CA]).thenReturn(trustRoot)
        whenever(signingStore.query(any<X509KeyStore.() -> List<X509Certificate>>())).thenReturn(mutableListOf(trustRoot))
        whenever(keyStore.query(any<X509KeyStore.() -> List<X509Certificate>>())).thenReturn(mutableListOf(trustRoot))
    }
}
