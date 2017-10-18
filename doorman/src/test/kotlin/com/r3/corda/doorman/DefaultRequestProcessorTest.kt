package com.r3.corda.doorman

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.r3.corda.doorman.persistence.*
import com.r3.corda.doorman.signer.DefaultCsrHandler
import com.r3.corda.doorman.signer.Signer
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.node.utilities.X509Utilities
import org.junit.Test
import kotlin.test.assertEquals

class DefaultRequestProcessorTest {
    @Test
    fun `get response`() {
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val cert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(locality = "London", organisation = "Test", country = "GB"), keyPair)

        val requestStorage: CertificationRequestStorage = mock {
            on { getRequest("New") }.thenReturn(CertificateSigningRequest(status = RequestStatus.New))
            on { getRequest("Signed") }.thenReturn(CertificateSigningRequest(status = RequestStatus.Signed, certificateData = CertificateData("", buildCertPath(cert.toX509Certificate()).encoded, CertificateStatus.VALID)))
            on { getRequest("Rejected") }.thenReturn(CertificateSigningRequest(status = RequestStatus.Rejected, rejectReason = "Random reason"))
        }
        val signer: Signer = mock()
        val requestProcessor = DefaultCsrHandler(requestStorage, signer)

        assertEquals(CertificateResponse.NotReady, requestProcessor.getResponse("random"))
        assertEquals(CertificateResponse.NotReady, requestProcessor.getResponse("New"))
        assertEquals(CertificateResponse.Ready(buildCertPath(cert.toX509Certificate())), requestProcessor.getResponse("Signed"))
        assertEquals(CertificateResponse.Unauthorised("Random reason"), requestProcessor.getResponse("Rejected"))
    }

    @Test
    fun `process request`() {
        val request1 = X509Utilities.createCertificateSigningRequest(CordaX500Name(locality = "London", organisation = "Test1", country = "GB"), "my@email.com", Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        val request2 = X509Utilities.createCertificateSigningRequest(CordaX500Name(locality = "London", organisation = "Test2", country = "GB"), "my@email.com", Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        val request3 = X509Utilities.createCertificateSigningRequest(CordaX500Name(locality = "London", organisation = "Test3", country = "GB"), "my@email.com", Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))

        val requestStorage: CertificationRequestStorage = mock {
            on { getRequests(RequestStatus.Approved) }.thenReturn(listOf(
                    CertificateSigningRequest(requestId = "1", request = request1.encoded),
                    CertificateSigningRequest(requestId = "2", request = request2.encoded),
                    CertificateSigningRequest(requestId = "3", request = request3.encoded)
            ))
        }
        val signer: Signer = mock()
        val requestProcessor = DefaultCsrHandler(requestStorage, signer)

        requestProcessor.processApprovedRequests()

        verify(signer, times(3)).sign(any())
        verify(requestStorage, times(1)).getRequests(any())
    }
}
