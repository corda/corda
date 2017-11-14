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
import com.r3.corda.networkmanage.common.utils.toX509Certificate
import com.r3.corda.networkmanage.doorman.signer.DefaultCsrHandler
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.node.utilities.X509Utilities
import org.junit.Test
import kotlin.test.assertEquals

class DefaultRequestProcessorTest : TestBase() {
    @Test
    fun `get response`() {
        val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val cert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(locality = "London", organisation = "Test", country = "GB"), keyPair)

        val requestStorage: CertificationRequestStorage = mock {
            on { getRequest("New") }.thenReturn(certificateSigningRequest())
            on { getRequest("Signed") }.thenReturn(certificateSigningRequest(status = RequestStatus.Signed, certData = certificateData("", CertificateStatus.VALID, buildCertPath(cert.toX509Certificate()))))
            on { getRequest("Rejected") }.thenReturn(certificateSigningRequest(status = RequestStatus.Rejected, remark = "Random reason"))
        }
        val signer: LocalSigner = mock()
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
                    certificateSigningRequest(requestId = "1", request = request1, status = RequestStatus.Approved),
                    certificateSigningRequest(requestId = "2", request = request2, status = RequestStatus.Approved),
                    certificateSigningRequest(requestId = "3", request = request3, status = RequestStatus.Approved)
            ))
        }
        val signer: LocalSigner = mock()
        val requestProcessor = DefaultCsrHandler(requestStorage, signer)

        requestProcessor.processApprovedRequests()

        verify(signer, times(3)).createSignedClientCertificate(any())
        verify(requestStorage, times(1)).getRequests(any())
    }
}
