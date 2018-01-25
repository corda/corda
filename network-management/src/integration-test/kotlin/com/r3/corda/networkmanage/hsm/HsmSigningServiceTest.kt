package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.generator.run
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import com.r3.corda.networkmanage.hsm.signer.HsmNetworkMapSigner
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.identity.CordaX500Name.Companion.parse
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME
import net.corda.nodeapi.internal.crypto.X509Utilities.createCertificateSigningRequest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HsmSigningServiceTest : HsmCertificateTest() {

    @Test
    fun `HSM signing service can sign network map data`() {
        // when root cert is created
        run(rootCertParameters)
        // when network map cert is created
        run(rootCertParameters.copy(
                certConfig = rootCertParameters.certConfig.copy(
                        keyGroup = NETWORK_MAP_CERT_KEY_GROUP,
                        rootKeyGroup = ROOT_CERT_KEY_GROUP,
                        certificateType = CertificateType.NETWORK_MAP,
                        subject = NETWORK_MAP_CERT_SUBJECT
                )
        ))
        // when doorman cert is created
        run(rootCertParameters.copy(
                certConfig = rootCertParameters.certConfig.copy(
                        keyGroup = DOORMAN_CERT_KEY_GROUP,
                        rootKeyGroup = ROOT_CERT_KEY_GROUP,
                        certificateType = CertificateType.INTERMEDIATE_CA,
                        subject = DOORMAN_CERT_SUBJECT
                )
        ))

        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput()

        // given HSM network map signer
        val signer = HsmNetworkMapSigner(Authenticator(
                provider = hsmSigningServiceConfig.createProvider(hsmSigningServiceConfig.networkMapKeyGroup),
                inputReader = userInput))

        // give random data to sign
        val toSign = secureRandomBytes(10)

        // when
        signer.signBytes(toSign)

        // No exception is thrown
    }

    @Test
    fun `HSM signing service can sign CSR data`() {
        // when root cert is created
        run(rootCertParameters)
        // when network map cert is created
        run(rootCertParameters.copy(
                certConfig = rootCertParameters.certConfig.copy(
                        keyGroup = NETWORK_MAP_CERT_KEY_GROUP,
                        rootKeyGroup = ROOT_CERT_KEY_GROUP,
                        certificateType = CertificateType.NETWORK_MAP,
                        subject = NETWORK_MAP_CERT_SUBJECT
                )
        ))
        // when doorman cert is created
        run(rootCertParameters.copy(
                certConfig = rootCertParameters.certConfig.copy(
                        keyGroup = DOORMAN_CERT_KEY_GROUP,
                        rootKeyGroup = ROOT_CERT_KEY_GROUP,
                        certificateType = CertificateType.INTERMEDIATE_CA,
                        subject = DOORMAN_CERT_SUBJECT
                )
        ))

        // given authenticated user
        val userInput = givenHsmUserAuthenticationInput()

        // given HSM CSR signer
        val signer = HsmCsrSigner(
                mock(),
                CORDA_INTERMEDIATE_CA,
                "",
                null,
                CORDA_ROOT_CA,
                3650,
                Authenticator(
                        provider = hsmSigningServiceConfig.createProvider(hsmSigningServiceConfig.doormanKeyGroup),
                        rootProvider = hsmSigningServiceConfig.createProvider(hsmSigningServiceConfig.rootKeyGroup),
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
}