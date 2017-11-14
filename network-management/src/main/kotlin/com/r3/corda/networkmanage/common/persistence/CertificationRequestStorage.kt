package com.r3.corda.networkmanage.common.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.CertPath

data class CertificateData(val publicKeyHash: String, val certStatus: CertificateStatus, val certPath: CertPath)

data class CertificateSigningRequest(val requestId: String,
                                     val legalName: String,
                                     val status: RequestStatus,
                                     val request: PKCS10CertificationRequest,
                                     val remark: String?,
                                     val modifiedBy: List<String>,
                                     val certData: CertificateData?)

/**
 *  Provide certificate signing request storage for the certificate signing server.
 */
interface CertificationRequestStorage {
    companion object {
        val DOORMAN_SIGNATURE = "Doorman"
    }

    /**
     * Persist [PKCS10CertificationRequest] in storage for further approval if it's a valid request.
     * If not then it will be automatically rejected and not subject to any approval process.
     * In both cases a randomly generated request ID is returned.
     * @param request request to be stored
     */
    fun saveRequest(request: PKCS10CertificationRequest): String

    /**
     * Retrieve certificate singing request using [requestId].
     * @return certificate signing request or null if the request does not exist
     */
    fun getRequest(requestId: String): CertificateSigningRequest?

    /**
     * Retrieve list of certificate signing request based on the [RequestStatus].
     */
    fun getRequests(requestStatus: RequestStatus): List<CertificateSigningRequest>

    /**
     * Approve the given request if it has not already been approved. Otherwise do nothing.
     * @param requestId id of the certificate signing request
     * @param approvedBy authority (its identifier) approving this request.
     */
    // TODO: Merge status changing methods.
    fun approveRequest(requestId: String, approvedBy: String)

    /**
     * Reject the given request using the given reason.
     * @param requestId id of the certificate signing request
     * @param rejectedBy authority (its identifier) rejecting this request.
     * @param rejectReason brief description of the rejection reason
     */
    fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String)

    /**
     * Store certificate path with [requestId], this will store the encoded [CertPath] and transit request status to [RequestStatus.SIGNED].
     * @param requestId id of the certificate signing request
     * @param signedBy authority (its identifier) signing this request.
     * @throws IllegalArgumentException if request is not found or not in Approved state.
     */
    fun putCertificatePath(requestId: String, certificates: CertPath, signedBy: List<String>)
}

sealed class CertificateResponse {
    object NotReady : CertificateResponse()
    data class Ready(val certificatePath: CertPath) : CertificateResponse()
    data class Unauthorised(val message: String) : CertificateResponse()
}

enum class RequestStatus {
    NEW, APPROVED, REJECTED, SIGNED
}

enum class CertificateStatus {
    VALID, SUSPENDED, REVOKED
}