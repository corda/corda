package com.r3.corda.networkmanage.common.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationListStorage
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestData
import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.random63BitValue
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.utilities.millis
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.net.URL
import java.security.cert.CRLReason
import java.security.cert.X509CRL
import java.time.Instant
import kotlin.test.assertNotNull

class CertificateRevocationListSignerTest : TestBase() {
    private val signer = TestSigner()
    private lateinit var crlStorage: CertificateRevocationListStorage
    private lateinit var crlSigner: CertificateRevocationListSigner

    @Before
    fun setUp() {
        crlStorage = mock()
        crlSigner = CertificateRevocationListSigner(crlStorage, DEV_INTERMEDIATE_CA.certificate, 600.millis, URL("http://dummy.com"), signer)
    }

    @Test
    fun `signCertificateRevocationList creates correct CRL and saves c`() {
        // given
        val approvedReq = givenCertificateRevocationRequest(RequestStatus.APPROVED)
        val revokedReq = givenCertificateRevocationRequest(RequestStatus.DONE)
        val signedBy = "Signer"

        // when
        crlSigner.createSignedCRL(listOf(approvedReq), listOf(revokedReq), signedBy)

        // then
        argumentCaptor<X509CRL>().apply {
            verify(crlStorage).saveCertificateRevocationList(capture(), eq(CrlIssuer.DOORMAN), eq(signedBy), any())
            val crl = firstValue
            crl.verify(DEV_INTERMEDIATE_CA.keyPair.public)
            assertNotNull(crl.getRevokedCertificate(approvedReq.certificateSerialNumber))
            assertNotNull(crl.getRevokedCertificate(revokedReq.certificateSerialNumber))
        }
    }

    private class TestSigner : Signer {
        override fun signBytes(data: ByteArray): DigitalSignatureWithCert {
            return DigitalSignatureWithCert(DEV_INTERMEDIATE_CA.certificate, DEV_INTERMEDIATE_CA.keyPair.private.sign(data).bytes)
        }
    }

    private fun givenCertificateRevocationRequest(status: RequestStatus): CertificateRevocationRequestData {
        return CertificateRevocationRequestData(
                SecureHash.randomSHA256().toString(),
                "CSR-ID-1",
                mock(),
                BigInteger.valueOf(random63BitValue()),
                Instant.now(),
                CordaX500Name.parse("CN=Bank A, O=$status, L=London, C=GB"),
                status,
                CRLReason.KEY_COMPROMISE,
                "Reporter")
    }

}