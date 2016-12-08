package com.r3.corda.netpermission.internal.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.Certificate

/**
 *  Provide certificate signing request storage for the certificate signing server.
 */
interface CertificationRequestStorage {
    /**
     * Persist [certificationData] in storage for further approval, returns randomly generated request ID.
     */
    fun saveRequest(certificationData: CertificationData): String

    /**
     * Retrieve certificate singing request and Host/IP information using [requestId].
     */
    fun getRequest(requestId: String): CertificationData?

    /**
     * Retrieve client certificate with provided [requestId].
     */
    fun getCertificate(requestId: String): Certificate?

    /**
     * Generate new certificate and store in storage using provided [certificateGenerator].
     */
    fun saveCertificate(requestId: String, certificateGenerator: (CertificationData) -> Certificate)

    /**
     * Retrieve list of request IDs waiting for approval.
     * TODO : This is used for the background thread to approve request automatically without KYC checks, should be removed after testnet.
     */
    fun pendingRequestIds(): List<String>
}

data class CertificationData(val hostName: String, val ipAddr: String, val request: PKCS10CertificationRequest)