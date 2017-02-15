package com.r3.corda.doorman.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.Certificate

/**
 *  Provide certificate signing request storage for the certificate signing server.
 */
interface CertificationRequestStorage {
    /**
     * Persist [certificationData] in storage for further approval if it's a valid request. If not then it will be automically
     * rejected and not subject to any approval process. In both cases a randomly generated request ID is returned.
     */
    fun saveRequest(certificationData: CertificationRequestData): String

    /**
     * Retrieve certificate singing request and Host/IP information using [requestId].
     */
    fun getRequest(requestId: String): CertificationRequestData?

    /**
     * Return the response for a previously saved request with ID [requestId].
     */
    fun getResponse(requestId: String): CertificateResponse

    /**
     * Approve the given request by generating and storing a new certificate using the provided generator.
     */
    fun approveRequest(requestId: String, generateCertificate: CertificationRequestData.() -> Certificate)

    /**
     * Reject the given request using the given reason.
     */
    fun rejectRequest(requestId: String, rejectReason: String)

    /**
     * Retrieve list of request IDs waiting for approval.
     * TODO : This is used for the background thread to approve request automatically without KYC checks, should be removed after testnet.
     */
    fun getPendingRequestIds(): List<String>
}

data class CertificationRequestData(val hostName: String, val ipAddress: String, val request: PKCS10CertificationRequest)

sealed class CertificateResponse {
    object NotReady : CertificateResponse()
    class Ready(val certificate: Certificate) : CertificateResponse()
    class Unauthorised(val message: String) : CertificateResponse()
}