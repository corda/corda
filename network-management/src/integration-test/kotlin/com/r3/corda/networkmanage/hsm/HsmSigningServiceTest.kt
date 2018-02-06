package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.HsmBaseTest
import com.r3.corda.networkmanage.common.persistence.PersistentNetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.initialiseSerialization
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.generator.run
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import com.r3.corda.networkmanage.hsm.signer.HsmSigner
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name.Companion.parse
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import net.corda.nodeapi.internal.crypto.X509Utilities.createCertificateSigningRequest
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HsmSigningServiceTest : HsmBaseTest() {

    @Before
    fun setUp() {
        loadOrCreateKeyStore(rootKeyStoreFile, TRUSTSTORE_PASSWORD)
    }

    @Test
    fun `HSM signing service can sign CSR data`() {
        // when root cert is created
        run(createGeneratorParameters(
                keyGroup = ROOT_CERT_KEY_GROUP,
                rootKeyGroup = null,
                certificateType = CertificateType.ROOT_CA,
                subject = ROOT_CERT_SUBJECT
        ))
        // when network map cert is created
        run(createGeneratorParameters(
                keyGroup = NETWORK_MAP_CERT_KEY_GROUP,
                rootKeyGroup = ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.NETWORK_MAP,
                subject = NETWORK_MAP_CERT_SUBJECT
        ))
        // when doorman cert is created
        run(createGeneratorParameters(
                keyGroup = DOORMAN_CERT_KEY_GROUP,
                rootKeyGroup = ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.INTERMEDIATE_CA,
                subject = DOORMAN_CERT_SUBJECT
        ))
        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput()

        // given HSM CSR signer
        val hsmSigningServiceConfig = createHsmSigningServiceConfig()
        val signer = HsmCsrSigner(
                mock(),
                hsmSigningServiceConfig.loadRootKeyStore(),
                "",
                null,
                3650,
                Authenticator(
                        provider = hsmSigningServiceConfig.createProvider(hsmSigningServiceConfig.doormanKeyGroup),
                        inputReader = userInput)
        )

        // give random data to sign
        val toSign = ApprovedCertificateRequestData(
                "test",
                createCertificateSigningRequest(
                        parse("O=R3Cev,L=London,C=GB").x500Principal,
                        "my@mail.com",
                        generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME)))

        // when
        signer.sign(listOf(toSign))

        // then
        assertNotNull(toSign.certPath)
        val certificates = toSign.certPath!!.certificates
        assertEquals(3, certificates.size)
    }

    @Test
    fun `HSM signing service can sign and serialize network map data to the Doorman DB`() {
        // when root cert is created
        run(createGeneratorParameters(
                keyGroup = ROOT_CERT_KEY_GROUP,
                rootKeyGroup = null,
                certificateType = CertificateType.ROOT_CA,
                subject = ROOT_CERT_SUBJECT
        ))
        // when network map cert is created
        run(createGeneratorParameters(
                keyGroup = NETWORK_MAP_CERT_KEY_GROUP,
                rootKeyGroup = ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.NETWORK_MAP,
                subject = NETWORK_MAP_CERT_SUBJECT
        ))
        // when doorman cert is created
        run(createGeneratorParameters(
                keyGroup = DOORMAN_CERT_KEY_GROUP,
                rootKeyGroup = ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.INTERMEDIATE_CA,
                subject = DOORMAN_CERT_SUBJECT
        ))

        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput()

        // given HSM network map signer
        val hsmSigningServiceConfig = createHsmSigningServiceConfig()
        val hsmDataSigner = HsmSigner(Authenticator(
                provider = hsmSigningServiceConfig.createProvider(hsmSigningServiceConfig.networkMapKeyGroup),
                inputReader = userInput))

        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        val networkMapStorage = PersistentNetworkMapStorage(database)

        // given network map parameters
        val networkMapParameters = testNetworkParameters(emptyList())
        val networkMapSigner = NetworkMapSigner(networkMapStorage, hsmDataSigner)

        // when
        initialiseSerialization()
        networkMapStorage.saveNetworkParameters(networkMapParameters, hsmDataSigner.signBytes(networkMapParameters.serialize().bytes))
        networkMapSigner.signNetworkMap()

        // then
        val signedNetworkMap = networkMapStorage.getCurrentNetworkMap()
        assertNotNull(signedNetworkMap)
        val persistedNetworkMap = signedNetworkMap!!.verified()
        assertEquals(networkMapParameters.serialize().hash, persistedNetworkMap.networkParameterHash)
        assertThat(persistedNetworkMap.nodeInfoHashes).isEmpty()
    }
}