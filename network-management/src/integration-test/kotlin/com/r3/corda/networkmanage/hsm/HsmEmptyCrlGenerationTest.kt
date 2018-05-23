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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.common.HsmBaseTest
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import com.r3.corda.networkmanage.hsm.generator.AutoAuthenticator
import com.r3.corda.networkmanage.hsm.generator.UserAuthenticationParameters
import com.r3.corda.networkmanage.hsm.generator.crl.CrlConfig
import com.r3.corda.networkmanage.hsm.generator.crl.GeneratorConfig
import com.r3.corda.networkmanage.hsm.generator.crl.RevocationConfig
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Test
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.r3.corda.networkmanage.hsm.generator.certificate.run as runCertificateGeneration
import com.r3.corda.networkmanage.hsm.generator.crl.run as runCrlGeneration

class HsmEmptyCrlGenerationTest : HsmBaseTest() {

    private lateinit var inputReader: InputReader

    @Before
    override fun setUp() {
        super.setUp()
        inputReader = mock()
        whenever(inputReader.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(inputReader.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password)
    }

    @Test
    fun `An empty CRL is generated`() {
        // when root cert is created
        runCertificateGeneration(createGeneratorParameters(
                keyGroup = ROOT_CERT_KEY_GROUP,
                rootKeyGroup = null,
                certificateType = CertificateType.ROOT_CA,
                subject = ROOT_CERT_SUBJECT
        ))

        // then root cert is persisted in the HSM
        AutoAuthenticator(createProviderConfig(ROOT_CERT_KEY_GROUP), HSM_USER_CONFIGS).connectAndAuthenticate { provider ->
            val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
            val rootCert = keyStore.getCertificate(CORDA_ROOT_CA) as X509Certificate
            assertEquals(rootCert.issuerX500Principal, rootCert.subjectX500Principal)
        }

        val generatedFile = tempFolder.newFile()
        runCrlGeneration(createCrlGeneratorParameters(CrlConfig(
                crlEndpoint = URL("http://test.com/crl"),
                filePath = generatedFile.toPath(),
                keyGroup = ROOT_CERT_KEY_GROUP,
                keySpecifier = 1,
                validDays = 1000,
                indirectIssuer = true,
                revocations = emptyList()), HSM_ROOT_USER_CONFIGS))
        val crl = CertificateFactory.getInstance("X.509")
                .generateCRL(FileUtils.readFileToByteArray(generatedFile).inputStream()) as X509CRL
        assertNotNull(crl)
        assertEquals(ROOT_CERT_SUBJECT, crl.issuerDN.name)
        assertTrue { crl.revokedCertificates.isEmpty() }
    }

    @Test
    fun `A non-empty CRL is generated`() {
        // when root cert is created
        runCertificateGeneration(createGeneratorParameters(
                keyGroup = ROOT_CERT_KEY_GROUP,
                rootKeyGroup = null,
                certificateType = CertificateType.ROOT_CA,
                subject = ROOT_CERT_SUBJECT
        ))

        // then root cert is persisted in the HSM
        AutoAuthenticator(createProviderConfig(ROOT_CERT_KEY_GROUP), HSM_USER_CONFIGS).connectAndAuthenticate { provider ->
            val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
            val rootCert = keyStore.getCertificate(CORDA_ROOT_CA) as X509Certificate
            assertEquals(rootCert.issuerX500Principal, rootCert.subjectX500Principal)
        }

        val generatedFile = tempFolder.newFile()
        val revokedSerialNumber = "1234567890"
        runCrlGeneration(createCrlGeneratorParameters(CrlConfig(
                crlEndpoint = URL("http://test.com/crl"),
                filePath = generatedFile.toPath(),
                keyGroup = ROOT_CERT_KEY_GROUP,
                keySpecifier = 1,
                validDays = 1000,
                indirectIssuer = false,
                revocations = listOf(
                        RevocationConfig(
                                certificateSerialNumber = "1234567890",
                                dateInMillis = 0,
                                reason = "KEY_COMPROMISE"
                        )
                )), HSM_ROOT_USER_CONFIGS))
        val crl = CertificateFactory.getInstance("X.509")
                .generateCRL(FileUtils.readFileToByteArray(generatedFile).inputStream()) as X509CRL
        assertNotNull(crl)
        assertEquals(ROOT_CERT_SUBJECT, crl.issuerDN.name)
        assertEquals(1, crl.revokedCertificates.size)
        val revoked = crl.revokedCertificates.first()
        assertEquals(revoked.serialNumber.toString(), revokedSerialNumber)
    }

    private fun createCrlGeneratorParameters(crlConfg: CrlConfig,
                                             userConfigs: List<UserAuthenticationParameters>): GeneratorConfig {
        return GeneratorConfig(
                hsmHost = hsmSimulator.host,
                hsmPort = hsmSimulator.port,
                trustStoreFile = rootKeyStoreFile,
                trustStorePassword = TRUSTSTORE_PASSWORD,
                userConfigs = userConfigs,
                crl = crlConfg
        )
    }
}