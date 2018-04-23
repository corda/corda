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
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.generator.UserAuthenticationParameters
import com.r3.corda.networkmanage.hsm.generator.run
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name.Companion.parse
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import net.corda.nodeapi.internal.crypto.X509Utilities.createCertificateSigningRequest
import org.junit.Test
import java.security.GeneralSecurityException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class HsmPermissionTest : HsmBaseTest() {

    /**
     * This test case scenario reflects the issue observed on 02.02.2018, when permissions user CXI_GROUP permissions
     * were wrongly configured on the PROD HSM box.
     *
     * Key groups are as follows:
     * "TEST.CORDACONNECT.ROOT"
     * "TEST.CORDACONNECT.OPS.NETMAP"
     * "TEST.CORDACONNECT.OPS.CERT"
     *
     * User CXI_GROUP configurations are as follows:
     * Root cert creator: TEST.CORDACONNECT.*
     * Doorman cert creator: TEST.CORDACONNECT.*
     * Networkmap cert creator: TEST.CORDACONNECT.*
     *
     * CSR signing user CXI_GROUP is as follows:
     * TEST.CORDACONNECT.OPS.CERT.*
     */
    @Test
    fun `HSM signing service cannot sign CSR data when HSM user CXI_GROUP permissions are wrongly configured`() {
        // given certs created
        givenCertificatesCreated(HSM_SUPER__USER_CONFIGS, HSM_SUPER__USER_CONFIGS, HSM_SUPER__USER_CONFIGS)
        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput(HSM_USERNAME_OPS_CERT_)

        // given HSM CSR signer
        val hsmSigningServiceConfig = createHsmSigningServiceConfig(createDoormanCertificateConfig(), null)
        val signer = HsmCsrSigner(
                mock(),
                hsmSigningServiceConfig.doorman!!.loadRootKeyStore(),
                "",
                null,
                3650,
                Authenticator(
                        provider = createProvider(
                                hsmSigningServiceConfig.doorman!!.keyGroup,
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

        // then
        // The GeneralSecurityException is thrown by the JCE layer.
        // This exception is caused by the CryptoServerException with code B0680001 - permission denied.
        assertFailsWith(GeneralSecurityException::class) {
            signer.sign(listOf(toSign))
        }
    }

    /**
     * This test case scenario reflects the fix for the issue observed on 02.02.2018, when permissions user CXI_GROUP permissions
     * were wrongly configured on the PROD HSM box.
     *
     * Key groups are as follows:
     * "TEST.CORDACONNECT.ROOT"
     * "TEST.CORDACONNECT.OPS.NETMAP"
     * "TEST.CORDACONNECT.OPS.CERT"
     *
     * User CXI_GROUP configurations are as follows:
     * Root cert creator: TEST.CORDACONNECT.*
     * Doorman cert creator: TEST.CORDACONNECT.*
     * Networkmap cert creator: TEST.CORDACONNECT.*
     *
     * CSR signing user CXI_GROUP is as follows:
     * TEST.CORDACONNECT.OPS.CERT
     */
    @Test
    fun `HSM signing service signs CSR data when HSM user CXI_GROUP permissions are correctly configured`() {
        // given certs created
        givenCertificatesCreated(HSM_SUPER__USER_CONFIGS, HSM_SUPER__USER_CONFIGS, HSM_SUPER__USER_CONFIGS)
        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput(HSM_USERNAME_OPS_CERT)

        // given HSM CSR signer
        val hsmSigningServiceConfig = createHsmSigningServiceConfig(createDoormanCertificateConfig(), null)
        val signer = HsmCsrSigner(
                mock(),
                hsmSigningServiceConfig.doorman!!.loadRootKeyStore(),
                "trustpass",
                null,
                3650,
                Authenticator(
                        provider = createProvider(
                                hsmSigningServiceConfig.doorman!!.keyGroup,
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
    }

    private fun givenCertificatesCreated(rootCertUserConfigs: List<UserAuthenticationParameters>,
                                         doormanCertUserConfigs: List<UserAuthenticationParameters>,
                                         netMapCertUserConfigs: List<UserAuthenticationParameters>) {
        // when root cert is created
        run(createGeneratorParameters(
                keyGroup = HsmBaseTest.ROOT_CERT_KEY_GROUP,
                rootKeyGroup = null,
                certificateType = CertificateType.ROOT_CA,
                subject = HsmBaseTest.ROOT_CERT_SUBJECT,
                hsmUserConfigs = rootCertUserConfigs))
        // when network map cert is created
        run(createGeneratorParameters(
                keyGroup = HsmBaseTest.NETWORK_MAP_CERT_KEY_GROUP,
                rootKeyGroup = HsmBaseTest.ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.NETWORK_MAP,
                subject = HsmBaseTest.NETWORK_MAP_CERT_SUBJECT,
                hsmUserConfigs = netMapCertUserConfigs
        ))
        // when doorman cert is created
        run(createGeneratorParameters(
                keyGroup = HsmBaseTest.DOORMAN_CERT_KEY_GROUP,
                rootKeyGroup = HsmBaseTest.ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.INTERMEDIATE_CA,
                subject = HsmBaseTest.DOORMAN_CERT_SUBJECT,
                hsmUserConfigs = doormanCertUserConfigs
        ))
    }
}