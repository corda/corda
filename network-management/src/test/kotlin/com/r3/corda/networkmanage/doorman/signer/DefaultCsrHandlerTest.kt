/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequestStorage
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import net.corda.core.crypto.Crypto
import net.corda.core.internal.CertRole
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals

class DefaultCsrHandlerTest : TestBase() {
    @Test
    fun getResponse() {
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val cert = X509Utilities.createSelfSignedCACertificate(X500Principal("O=Test,L=London,C=GB"), keyPair)

        val requestStorage: CertificateSigningRequestStorage = mock {
            on { getRequest("New") }.thenReturn(certificateSigningRequest())
            on { getRequest("Signed") }.thenReturn(certificateSigningRequest(
                    status = RequestStatus.DONE,
                    certData = certificateData(CertificateStatus.VALID, X509Utilities.buildCertPath(cert))
            ))
            on { getRequest("Rejected") }.thenReturn(certificateSigningRequest(status = RequestStatus.REJECTED, remark = "Random reason"))
        }
        val requestProcessor = DefaultCsrHandler(requestStorage, null)

        assertEquals(CertificateResponse.NotReady, requestProcessor.getResponse("random"))
        assertEquals(CertificateResponse.NotReady, requestProcessor.getResponse("New"))
        assertEquals(CertificateResponse.Ready(X509Utilities.buildCertPath(cert)), requestProcessor.getResponse("Signed"))
        assertEquals(CertificateResponse.Unauthorised("Random reason"), requestProcessor.getResponse("Rejected"))
    }

    @Test
    fun processApprovedRequests() {
        val requests = (1..3).map {
            X509Utilities.createCertificateSigningRequest(
                    X500Principal("O=Test$it,L=London,C=GB"),
                    "my@email.com",
                    Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        }

        val requestStorage: CertificateSigningRequestStorage = mock {
            on { getRequests(RequestStatus.APPROVED) }.thenReturn(listOf(
                    certificateSigningRequest(requestId = "1", request = requests[0], status = RequestStatus.APPROVED),
                    certificateSigningRequest(requestId = "2", request = requests[1], status = RequestStatus.APPROVED)
            ))
            on { getRequests(RequestStatus.REJECTED) }.thenReturn(listOf(
                    certificateSigningRequest(requestId = "3", request = requests[2], status = RequestStatus.REJECTED)
            ))
        }

        val (rootCa, csrCa) = createDevIntermediateCaCertPath()
        val csrCertPathAndKey = CertPathAndKey(listOf(csrCa.certificate, rootCa.certificate), csrCa.keyPair.private)
        val requestProcessor = DefaultCsrHandler(requestStorage, csrCertPathAndKey)

        requestProcessor.processRequests()

        val certPathCapture = argumentCaptor<CertPath>()

        // Verify only the approved requests are taken
        verify(requestStorage, times(1)).getRequests(RequestStatus.APPROVED)
        verify(requestStorage, times(1)).putCertificatePath(eq("1"), certPathCapture.capture(), eq(DOORMAN_SIGNATURE))
        verify(requestStorage, times(1)).putCertificatePath(eq("2"), certPathCapture.capture(), eq(DOORMAN_SIGNATURE))

        // Then make sure the generated node cert paths are correct
        certPathCapture.allValues.forEachIndexed { index, certPath ->
            X509Utilities.validateCertificateChain(rootCa.certificate, certPath.x509Certificates)
            assertThat(certPath.certificates).hasSize(3).element(1).isEqualTo(csrCa.certificate)
            (certPath.certificates[0] as X509Certificate).apply {
                assertThat(CertRole.extract(this)).isEqualTo(CertRole.NODE_CA)
                assertThat(publicKey).isEqualTo(Crypto.toSupportedPublicKey(requests[index].subjectPublicKeyInfo))
                assertThat(subjectX500Principal).isEqualTo(X500Principal("O=Test${index + 1},L=London,C=GB"))
                // Is CA
                assertThat(basicConstraints != -1)
            }
        }
    }

    @Test
    fun `process approved service identity request`() {
        val requests = (1..3).map {
            X509Utilities.createCertificateSigningRequest(
                    X500Principal("O=Test$it,L=London,C=GB"),
                    "my@email.com",
                    Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME), certRole = CertRole.SERVICE_IDENTITY)
        }

        val requestStorage: CertificateSigningRequestStorage = mock {
            on { getRequests(RequestStatus.APPROVED) }.thenReturn(listOf(
                    certificateSigningRequest(requestId = "1", request = requests[0], status = RequestStatus.APPROVED)
            ))
        }

        val (rootCa, csrCa) = createDevIntermediateCaCertPath()
        val csrCertPathAndKey = CertPathAndKey(listOf(csrCa.certificate, rootCa.certificate), csrCa.keyPair.private)
        val requestProcessor = DefaultCsrHandler(requestStorage, csrCertPathAndKey)

        requestProcessor.processRequests()

        val certPathCapture = argumentCaptor<CertPath>()

        // Verify only the approved requests are taken
        verify(requestStorage, times(1)).getRequests(RequestStatus.APPROVED)
        verify(requestStorage, times(1)).putCertificatePath(eq("1"), certPathCapture.capture(), eq(DOORMAN_SIGNATURE))

        // Then make sure the generated node cert paths are correct
        certPathCapture.allValues.forEachIndexed { index, certPath ->
            X509Utilities.validateCertificateChain(rootCa.certificate, certPath.x509Certificates)
            assertThat(certPath.certificates).hasSize(3).element(1).isEqualTo(csrCa.certificate)
            (certPath.certificates[0] as X509Certificate).apply {
                assertThat(CertRole.extract(this)).isEqualTo(CertRole.SERVICE_IDENTITY)
                assertThat(publicKey).isEqualTo(Crypto.toSupportedPublicKey(requests[index].subjectPublicKeyInfo))
                assertThat(subjectX500Principal).isEqualTo(X500Principal("O=Test${index + 1},L=London,C=GB"))
                // Not a CA
                assertThat(basicConstraints == -1)
            }
        }
    }
}
