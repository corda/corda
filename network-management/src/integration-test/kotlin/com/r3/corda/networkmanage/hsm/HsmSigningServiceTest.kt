/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.HsmBaseTest
import com.r3.corda.networkmanage.common.persistence.PersistentNetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.common.utils.initialiseSerialization
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.generator.certificate.run
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import com.r3.corda.networkmanage.hsm.signer.HsmSigner
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name.Companion.parse
import net.corda.core.internal.CertRole
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import net.corda.nodeapi.internal.crypto.X509Utilities.createCertificateSigningRequest
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import net.corda.nodeapi.internal.crypto.x509
import net.corda.testing.common.internal.testNetworkParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class HsmSigningServiceTest : HsmBaseTest() {
    @Before
    override fun setUp() {
        super.setUp()
        loadOrCreateKeyStore(rootKeyStoreFile, TRUSTSTORE_PASSWORD)
    }

    @Test
    fun `HSM signing service can sign node CSR data`() {
        setupCertificates()

        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput()

        // given HSM CSR signer
        val hsmSigningServiceConfig = createHsmSigningServiceConfig(createDoormanCertificateConfig(), null)
        val doormanCertificateConfig = hsmSigningServiceConfig.doorman!!
        val signer = HsmCsrSigner(
                mock(),
                doormanCertificateConfig.loadRootKeyStore(),
                "",
                null,
                3650,
                Authenticator(
                        provider = createProvider(
                                doormanCertificateConfig.keyGroup,
                                hsmSigningServiceConfig.keySpecifier,
                                hsmSigningServiceConfig.device),
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
        // Is a CA
        assertNotEquals(-1, certificates.first().x509.basicConstraints)
        assertEquals(CertRole.NODE_CA, CertRole.extract(certificates.first().x509))
    }

    @Test
    fun `HSM signing service can sign service identity CSR data`() {
        setupCertificates()

        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput()

        // given HSM CSR signer
        val hsmSigningServiceConfig = createHsmSigningServiceConfig(createDoormanCertificateConfig(), null)
        val doormanCertificateConfig = hsmSigningServiceConfig.doorman!!
        val signer = HsmCsrSigner(
                mock(),
                doormanCertificateConfig.loadRootKeyStore(),
                "",
                null,
                3650,
                Authenticator(
                        provider = createProvider(
                                doormanCertificateConfig.keyGroup,
                                hsmSigningServiceConfig.keySpecifier,
                                hsmSigningServiceConfig.device),
                        inputReader = userInput)
        )

        // give random data to sign
        val toSign = ApprovedCertificateRequestData(
                "test",
                createCertificateSigningRequest(
                        parse("O=R3Cev,L=London,C=GB").x500Principal,
                        "my@mail.com",
                        generateKeyPair(DEFAULT_TLS_SIGNATURE_SCHEME),
                        certRole = CertRole.SERVICE_IDENTITY))

        // when
        signer.sign(listOf(toSign))

        // then
        assertNotNull(toSign.certPath)
        val certificates = toSign.certPath!!.certificates
        assertEquals(3, certificates.size)
        // Not a CA
        assertEquals(-1, certificates.first().x509.basicConstraints)
        assertEquals(CertRole.SERVICE_IDENTITY, CertRole.extract(certificates.first().x509))
    }

    @Test
    fun `HSM signing service can sign and serialize network map data to the Doorman DB`() {
        setupCertificates()

        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput()

        // given HSM network map signer
        val hsmSigningServiceConfig = createHsmSigningServiceConfig(null, createNetworkMapCertificateConfig())
        val networkMapCertificateConfig = hsmSigningServiceConfig.networkMap!!
        val hsmDataSigner = HsmSigner(Authenticator(
                provider = createProvider(
                        networkMapCertificateConfig.keyGroup,
                        hsmSigningServiceConfig.keySpecifier,
                        hsmSigningServiceConfig.device),
                inputReader = userInput),
                keyName = CORDA_NETWORK_MAP)

        val database = configureDatabase(makeTestDataSourceProperties(), makeTestDatabaseProperties())
        val networkMapStorage = PersistentNetworkMapStorage(database)

        // given network map parameters
        val networkMapParameters = testNetworkParameters(emptyList())
        val networkMapSigner = NetworkMapSigner(networkMapStorage, hsmDataSigner)

        // when
        initialiseSerialization()
        networkMapStorage.saveNetworkParameters(networkMapParameters, hsmDataSigner.signBytes(networkMapParameters.serialize().bytes))
        networkMapSigner.signNetworkMaps()

        // then
        val persistedNetworkMap = networkMapStorage.getNetworkMaps().publicNetworkMap!!.toSignedNetworkMap().verified()
        assertEquals(networkMapParameters.serialize().hash, persistedNetworkMap.networkParameterHash)
        assertThat(persistedNetworkMap.nodeInfoHashes).isEmpty()
    }

    private fun setupCertificates() {
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
    }
}