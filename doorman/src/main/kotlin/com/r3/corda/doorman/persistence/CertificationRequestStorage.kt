package com.r3.corda.doorman.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.CertPath

/**
 *  Provide certificate signing request storage for the certificate signing server.
 */
interface CertificationRequestStorage {

    companion object {
        val DOORMAN_SIGNATURE = listOf("Doorman")
    }

    /**
     * Persist [certificationData] in storage for further approval if it's a valid request. If not then it will be automatically
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
     * Approve the given request if it has not already been approved. Otherwise do nothing.
     *
     * @return True if the request has been approved and false otherwise.
     */
    fun approveRequest(requestId: String, approvedBy: String = DOORMAN_SIGNATURE.first()): Boolean

    /**
     * Signs the certificate signing request by assigning the given certificate.
     *
     * @return True if the request has been signed and false otherwise.
     */
    fun signCertificate(requestId: String, signedBy: List<String> = DOORMAN_SIGNATURE, generateCertificate: CertificationRequestData.() -> CertPath): Boolean

    /**
     * Reject the given request using the given reason.
     */
    fun rejectRequest(requestId: String, rejectedBy: String = DOORMAN_SIGNATURE.first(), rejectReason: String)

    /**
     * Retrieve list of request IDs waiting for approval.
     */
    fun getNewRequestIds(): List<String>

    /**
     * Retrieve list of approved request IDs.
     */
    fun getApprovedRequestIds(): List<String>

    /**
     * Retrieve list of signed request IDs.
     */
    fun getSignedRequestIds(): List<String>
}

data class CertificationRequestData(val hostName: String, val ipAddress: String, val request: PKCS10CertificationRequest)

sealed class CertificateResponse {
    object NotReady : CertificateResponse()
    class Ready(val certificatePath: CertPath) : CertificateResponse()
    class Unauthorised(val message: String) : CertificateResponse()
}