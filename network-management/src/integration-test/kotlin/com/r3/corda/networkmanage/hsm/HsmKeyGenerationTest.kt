package com.r3.corda.networkmanage.hsm

import com.r3.corda.networkmanage.HsmSimulator
import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.generator.*
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HsmKeyGenerationTest {

    companion object {
        val KEY_PASSWORD = "PASSWORD"
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val hsmSimulator: HsmSimulator = HsmSimulator()

    private val rootCertParameters: GeneratorParameters by lazy {
        GeneratorParameters(
                hsmHost = hsmSimulator.host,
                hsmPort = hsmSimulator.port,
                trustStoreDirectory = tempFolder.root.toPath(),
                trustStorePassword = "",
                userConfigs = listOf(UserAuthenticationParameters(
                        username = "INTEGRATION_TEST",
                        authMode = AuthMode.PASSWORD,
                        authToken = "INTEGRATION_TEST",
                        keyFilePassword = null
                )),
                certConfig = CertificateConfiguration(
                        keySpecifier = 1,
                        keyGroup = "DEV.DOORMAN",
                        storeKeysExternal = false,
                        privateKeyPassword = KEY_PASSWORD,
                        rootPrivateKeyPassword = KEY_PASSWORD,
                        subject = "CN=Corda Root, O=R3Cev, L=London, C=GB",
                        validDays = 3650,
                        keyCurve = "NIST-P256",
                        certificateType = CertificateType.ROOT_CA,
                        keyExport = 0,
                        keyGenMechanism = 4,
                        keyOverride = 0,
                        crlIssuer = null,
                        crlDistributionUrl = null
                )
        )
    }

    private val providerConfig: CryptoServerProviderConfig by lazy {
        CryptoServerProviderConfig(
                Device = "${rootCertParameters.hsmPort}@${rootCertParameters.hsmHost}",
                KeySpecifier = rootCertParameters.certConfig.keySpecifier,
                KeyGroup = rootCertParameters.certConfig.keyGroup,
                StoreKeysExternal = rootCertParameters.certConfig.storeKeysExternal)
    }

    @Test
    fun `Authenticator executes the block once user is successfully authenticated`() {
        // given
        val authenticator = AutoAuthenticator(providerConfig, rootCertParameters.userConfigs)
        val rootCertGenerator = KeyCertificateGenerator(rootCertParameters)
        // when root cert is created
        authenticator.connectAndAuthenticate { provider ->
            rootCertGenerator.generate(provider)
            // then
            val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
            val rootCert = keyStore.getCertificate(CORDA_ROOT_CA) as X509Certificate
            assertEquals(rootCert.issuerX500Principal, rootCert.subjectX500Principal)
        }
        // when network map cert is created
        val networkMapCertGenerator = KeyCertificateGenerator(rootCertParameters.copy(
                certConfig = rootCertParameters.certConfig.copy(
                        certificateType = CertificateType.NETWORK_MAP,
                        subject = "CN=Corda NM, O=R3Cev, L=London, C=GB"
                )
        ))
        authenticator.connectAndAuthenticate { provider ->
            networkMapCertGenerator.generate(provider)
            // then
            val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
            val rootCert = keyStore.getCertificate(CORDA_ROOT_CA) as X509Certificate
            val networkMapCert = keyStore.getCertificate(CORDA_NETWORK_MAP) as X509Certificate
            assertNotNull(networkMapCert)
            assertEquals(rootCert.subjectX500Principal, networkMapCert.issuerX500Principal)
        }
        // when csr cert is created
        val csrCertGenerator = KeyCertificateGenerator(rootCertParameters.copy(
                certConfig = rootCertParameters.certConfig.copy(
                        certificateType = CertificateType.INTERMEDIATE_CA,
                        subject = "CN=Corda CSR, O=R3Cev, L=London, C=GB"
                )
        ))
        authenticator.connectAndAuthenticate { provider ->
            csrCertGenerator.generate(provider)
            // then
            val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
            val rootCert = keyStore.getCertificate(CORDA_ROOT_CA) as X509Certificate
            val csrCert = keyStore.getCertificate(CORDA_INTERMEDIATE_CA) as X509Certificate
            assertNotNull(csrCert)
            assertEquals(rootCert.subjectX500Principal, csrCert.issuerX500Principal)
        }
    }
}