package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.doorman.signer.DefaultCsrHandler
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Test
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals

class DefaultRequestProcessorTest : TestBase() {
    @Test
    fun `get response`() {
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val cert = X509Utilities.createSelfSignedCACertificate(X500Principal("O=Test,L=London,C=GB"), keyPair)

        val requestStorage: CertificationRequestStorage = mock {
            on { getRequest("New") }.thenReturn(certificateSigningRequest())
            on { getRequest("Signed") }.thenReturn(certificateSigningRequest(status = RequestStatus.SIGNED, certData = certificateData("", CertificateStatus.VALID, buildCertPath(cert))))
            on { getRequest("Rejected") }.thenReturn(certificateSigningRequest(status = RequestStatus.REJECTED, remark = "Random reason"))
        }
        val signer: LocalSigner = mock()
        val requestProcessor = DefaultCsrHandler(requestStorage, signer)

        assertEquals(CertificateResponse.NotReady, requestProcessor.getResponse("random"))
        assertEquals(CertificateResponse.NotReady, requestProcessor.getResponse("New"))
        assertEquals(CertificateResponse.Ready(buildCertPath(cert)), requestProcessor.getResponse("Signed"))
        assertEquals(CertificateResponse.Unauthorised("Random reason"), requestProcessor.getResponse("Rejected"))
    }

    @Test
    fun `process request`() {
        val (request1, request2, request3) = (1..3).map {
            X509Utilities.createCertificateSigningRequest(X500Principal("O=Test1,L=London,C=GB"), "my@email.com", Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        }

        val requestStorage: CertificationRequestStorage = mock {
            on { getRequests(RequestStatus.APPROVED) }.thenReturn(listOf(
                    certificateSigningRequest(requestId = "1", request = request1, status = RequestStatus.APPROVED),
                    certificateSigningRequest(requestId = "2", request = request2, status = RequestStatus.APPROVED),
                    certificateSigningRequest(requestId = "3", request = request3, status = RequestStatus.APPROVED)
            ))
        }
        val signer: LocalSigner = mock()
        val requestProcessor = DefaultCsrHandler(requestStorage, signer)

        requestProcessor.processApprovedRequests()

        verify(signer, times(3)).createSignedClientCertificate(any())
        verify(requestStorage, times(1)).getRequests(any())
    }
}
