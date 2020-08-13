package net.corda.node.internal

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.DISTRIBUTED_NOTARY_COMPOSITE_KEY_ALIAS
import net.corda.nodeapi.internal.crypto.X509Utilities.DISTRIBUTED_NOTARY_KEY_ALIAS
import net.corda.nodeapi.internal.crypto.X509Utilities.NODE_IDENTITY_KEY_ALIAS
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.PublicKey

class KeyStoreHandlerTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val certificateDir get() = tempFolder.root.toPath() / "certificates"

    private val config = mock<NodeConfiguration>()

    private val keyStore get() = config.signingCertificateStore.get()

    private lateinit var cryptoService: BCCryptoService

    private lateinit var keyStoreHandler: KeyStoreHandler

    @Before
    fun before() {
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificateDir)
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir)
        p2pSslOptions.configureDevKeyAndTrustStores(ALICE_NAME, signingCertificateStore, certificateDir)

        whenever(config.devMode).thenReturn(false)
        whenever(config.signingCertificateStore).thenReturn(signingCertificateStore)
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)
        whenever(config.myLegalName).thenReturn(ALICE_NAME)

        cryptoService = BCCryptoService(ALICE_NAME.x500Principal, signingCertificateStore)
        keyStoreHandler = KeyStoreHandler(config, cryptoService)
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
        val keyPair = Crypto.generateKeyPair()
        val tlsKeyPair = Crypto.generateKeyPair()
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
        val devCryptoService = BCCryptoService(config.myLegalName.x500Principal, signingCertificateStore)

        whenever(config.devMode).thenReturn(true)
        whenever(config.signingCertificateStore).thenReturn(signingCertificateStore)
        whenever(config.p2pSslOptions).thenReturn(p2pSslOptions)
        whenever(config.certificatesDirectory).thenReturn(devCertificateDir)

        assertThat(devCryptoService.containsKey(NODE_IDENTITY_KEY_ALIAS)).isFalse()

        KeyStoreHandler(config, devCryptoService).initKeyStores()

        assertThat(config.p2pSslOptions.trustStore.get().contains(CORDA_ROOT_CA)).isTrue()
        assertThat(config.p2pSslOptions.keyStore.get().contains(CORDA_CLIENT_TLS)).isTrue()
        assertThat(config.signingCertificateStore.get().contains(NODE_IDENTITY_KEY_ALIAS)).isTrue()
        assertThat(devCryptoService.containsKey(NODE_IDENTITY_KEY_ALIAS)).isTrue()
    }

    @Test(timeout = 300_000)
    fun `load node identity`() {
        keyStoreHandler.initKeyStores()
        val identities = keyStoreHandler.obtainIdentities()

        val certificate = keyStore[NODE_IDENTITY_KEY_ALIAS]
        assertThat(identities.nodeIdentity.certificate).isEqualTo(certificate)
        assertThat(identities.notaryIdentity).isNull()
        assertThat(identities.signingKeys).containsExactly(KeyStoreHandler.KeyAndAlias(certificate.publicKey, NODE_IDENTITY_KEY_ALIAS))
        assertThat(identities.oldNotaryKeys).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `load node identity without node CA`() {
        assertThat(keyStore[CORDA_CLIENT_CA]).isNotNull
        keyStore.update { internal.deleteEntry(CORDA_CLIENT_CA) }

        keyStoreHandler.initKeyStores()
        val identities = keyStoreHandler.obtainIdentities()

        val certificate = keyStore[NODE_IDENTITY_KEY_ALIAS]
        assertThat(identities.nodeIdentity.certificate).isEqualTo(certificate)
        assertThat(identities.notaryIdentity).isNull()
        assertThat(identities.signingKeys).containsExactly(KeyStoreHandler.KeyAndAlias(certificate.publicKey, NODE_IDENTITY_KEY_ALIAS))
        assertThat(identities.oldNotaryKeys).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `load node identity with missing alias`() {
        keyStore.update { internal.deleteEntry(NODE_IDENTITY_KEY_ALIAS) }

        keyStoreHandler.initKeyStores()
        assertThatThrownBy {
            keyStoreHandler.obtainIdentities()
        }.hasMessageContaining("node identity key is not in the keyStore file")
    }

    @Test(timeout = 300_000)
    fun `load node identity with missing key`() {
        keyStoreHandler = KeyStoreHandler(config, mock())

        keyStoreHandler.initKeyStores()
        assertThatThrownBy {
            keyStoreHandler.obtainIdentities()
        }.hasMessageContaining("Key for node identity alias '$NODE_IDENTITY_KEY_ALIAS' not found in CryptoService")
    }

    @Test(timeout = 300_000)
    fun `load node identity with unknown root`() {
        val newRoot = X509Utilities.createSelfSignedCACertificate(ALICE_NAME.x500Principal, Crypto.generateKeyPair())

        keyStore.update {
            val privateKey = getPrivateKey(NODE_IDENTITY_KEY_ALIAS, DEV_CA_KEY_STORE_PASS)
            val certificates = getCertificateChain(NODE_IDENTITY_KEY_ALIAS)
            setPrivateKey(NODE_IDENTITY_KEY_ALIAS, privateKey, certificates.dropLast(1) + newRoot, DEV_CA_KEY_STORE_PASS)
        }

        keyStoreHandler.initKeyStores()
        assertThatThrownBy {
            keyStoreHandler.obtainIdentities()
        }.hasMessageContaining("Certificate for node identity must chain to the trusted root")
    }

    @Test(timeout = 300_000)
    fun `load node identity with wrong legal name`() {
        whenever(config.myLegalName).thenReturn(BOB_NAME)

        keyStoreHandler.initKeyStores()
        assertThatThrownBy {
            keyStoreHandler.obtainIdentities()
        }.hasMessageContaining("The configured legalName").hasMessageContaining("doesn't match what's in the key store")
    }

    @Test(timeout = 300_000)
    fun `load node identity with wrong certificate path`() {
        keyStore.update {
            val privateKey = getPrivateKey(NODE_IDENTITY_KEY_ALIAS, DEV_CA_KEY_STORE_PASS)
            val certificates = getCertificateChain(NODE_IDENTITY_KEY_ALIAS)
            setPrivateKey(NODE_IDENTITY_KEY_ALIAS, privateKey, certificates.take(1) + certificates.drop(2), DEV_CA_KEY_STORE_PASS)
        }

        keyStoreHandler.initKeyStores()
        assertThatThrownBy {
            keyStoreHandler.obtainIdentities()
        }.hasMessageContaining("Cert path failed to validate")
    }

    @Test(timeout = 300_000)
    fun `load old style notary identity`() {
        whenever(config.notary).thenReturn(mock())

        keyStoreHandler.initKeyStores()
        val identities = keyStoreHandler.obtainIdentities()

        val certificate = keyStore[NODE_IDENTITY_KEY_ALIAS]
        assertThat(identities.nodeIdentity.certificate).isEqualTo(certificate)
        assertThat(identities.notaryIdentity).isNotNull
        assertThat(identities.notaryIdentity!!.certificate).isEqualTo(certificate)
        assertThat(identities.signingKeys).containsExactly(KeyStoreHandler.KeyAndAlias(certificate.publicKey, NODE_IDENTITY_KEY_ALIAS))
        assertThat(identities.oldNotaryKeys).isEmpty()
    }

    private fun createNotaryCertificate(publicKey: PublicKey, name: CordaX500Name) = X509Utilities.createCertificate(
            CertificateType.SERVICE_IDENTITY,
            DEV_INTERMEDIATE_CA.certificate,
            DEV_INTERMEDIATE_CA.keyPair,
            name.x500Principal,
            publicKey)

    private fun generateNotaryIdentity(alias: String, name: CordaX500Name): PublicKey {
        val keyPair = Crypto.generateKeyPair()
        val certificates = listOf(createNotaryCertificate(keyPair.public, name), DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate)
        keyStore.update {
            setPrivateKey(alias, keyPair.private, certificates, DEV_CA_KEY_STORE_PASS)
        }
        cryptoService.resyncKeystore()
        return keyPair.public
    }

    @Test(timeout = 300_000)
    fun `load notary identity`() {
        val notaryConfig = mock<NotaryConfig>()
        whenever(notaryConfig.serviceLegalName).thenReturn(BOB_NAME)
        whenever(config.notary).thenReturn(notaryConfig)

        generateNotaryIdentity(DISTRIBUTED_NOTARY_KEY_ALIAS, BOB_NAME)

        keyStoreHandler.initKeyStores()
        val identities = keyStoreHandler.obtainIdentities()

        val nodeCert = keyStore[NODE_IDENTITY_KEY_ALIAS]
        val notaryCert = keyStore[DISTRIBUTED_NOTARY_KEY_ALIAS]
        assertThat(identities.nodeIdentity.certificate).isEqualTo(nodeCert)
        assertThat(identities.notaryIdentity).isNotNull
        assertThat(identities.notaryIdentity!!.certificate).isEqualTo(notaryCert)
        assertThat(identities.signingKeys.map { it.key }).containsExactly(nodeCert.publicKey, notaryCert.publicKey)
        assertThat(identities.signingKeys.map { it.alias }).containsExactly(NODE_IDENTITY_KEY_ALIAS, DISTRIBUTED_NOTARY_KEY_ALIAS)
        assertThat(identities.oldNotaryKeys).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `load notary identity with wrong legal name`() {
        val notaryConfig = mock<NotaryConfig>()
        whenever(notaryConfig.serviceLegalName).thenReturn(BOB_NAME)
        whenever(config.notary).thenReturn(notaryConfig)

        generateNotaryIdentity(DISTRIBUTED_NOTARY_KEY_ALIAS, ALICE_NAME)

        keyStoreHandler.initKeyStores()
        assertThatThrownBy {
            keyStoreHandler.obtainIdentities()
        }.hasMessageContaining("The configured legalName").hasMessageContaining("doesn't match what's in the key store")
    }

    @Test(timeout = 300_000)
    fun `load notary composite identity`() {
        val notaryConfig = mock<NotaryConfig>()
        whenever(notaryConfig.serviceLegalName).thenReturn(BOB_NAME)
        whenever(config.notary).thenReturn(notaryConfig)

        val notaryKey = generateNotaryIdentity(DISTRIBUTED_NOTARY_KEY_ALIAS, BOB_NAME)
        val compositeKey = CompositeKey.Builder().addKey(notaryKey).build()
        keyStore[DISTRIBUTED_NOTARY_COMPOSITE_KEY_ALIAS] = createNotaryCertificate(compositeKey, BOB_NAME)

        keyStoreHandler.initKeyStores()
        val identities = keyStoreHandler.obtainIdentities()

        val nodeCert = keyStore[NODE_IDENTITY_KEY_ALIAS]
        assertThat(identities.nodeIdentity.certificate).isEqualTo(nodeCert)
        assertThat(identities.notaryIdentity).isNotNull
        assertThat(identities.notaryIdentity!!.certificate).isEqualTo(keyStore[DISTRIBUTED_NOTARY_COMPOSITE_KEY_ALIAS])
        assertThat(identities.signingKeys.map { it.key }).containsExactly(nodeCert.publicKey, notaryKey)
        assertThat(identities.signingKeys.map { it.alias }).containsExactly(NODE_IDENTITY_KEY_ALIAS, DISTRIBUTED_NOTARY_KEY_ALIAS)
        assertThat(identities.oldNotaryKeys).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `load notary composite identity with wrong legal name`() {
        val notaryConfig = mock<NotaryConfig>()
        whenever(notaryConfig.serviceLegalName).thenReturn(BOB_NAME)
        whenever(config.notary).thenReturn(notaryConfig)

        val notaryKey = generateNotaryIdentity(DISTRIBUTED_NOTARY_KEY_ALIAS, BOB_NAME)
        val compositeKey = CompositeKey.Builder().addKey(notaryKey).build()
        keyStore[DISTRIBUTED_NOTARY_COMPOSITE_KEY_ALIAS] = createNotaryCertificate(compositeKey, ALICE_NAME)

        keyStoreHandler.initKeyStores()
        assertThatThrownBy {
            keyStoreHandler.obtainIdentities()
        }.hasMessageContaining("The configured legalName").hasMessageContaining("doesn't match what's in the key store")
    }
}