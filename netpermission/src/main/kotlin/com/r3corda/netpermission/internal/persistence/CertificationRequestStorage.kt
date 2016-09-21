package com.r3corda.netpermission.internal.persistence

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
     * Retrieve approved certificate singing request and Host/IP information using [requestId].
     * Returns [CertificationData] if request has been approved, else returns null.
     */
    fun getApprovedRequest(requestId: String): CertificationData?

    /**
     * Retrieve client certificate with provided [requestId].
     * Generate new certificate and store in storage using provided [certificateGenerator] if certificate does not exist.
     */
    fun getOrElseCreateCertificate(requestId: String, certificateGenerator: () -> Certificate): Certificate
}

data class CertificationData(val hostName: String, val ipAddr: String, val request: PKCS10CertificationRequest)