package com.r3.corda.networkmanage.common.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.CertPath

/**
 *  Provide certificate signing request storage for the certificate signing server.
 */
interface CertificationRequestStorage {
    companion object {
        val DOORMAN_SIGNATURE = "Doorman"
    }

    /**
     * Persist [PKCS10CertificationRequest] in storage for further approval if it's a valid request. If not then it will be automatically
     * rejected and not subject to any approval process. In both cases a randomly generated request ID is returned.
     * @param certificationData certificate request data to be persisted.
     * @param createdBy authority (its identifier) creating this request.
     */
    fun saveRequest(rawRequest: PKCS10CertificationRequest): String

    /**
     * Retrieve certificate singing request using [requestId].
     */
    fun getRequest(requestId: String): CertificateSigningRequest?

    /**
     * Retrieve list of certificate singing request base on the [RequestStatus].
     */
    fun getRequests(requestStatus: RequestStatus): List<CertificateSigningRequest>

    /**
     * Approve the given request if it has not already been approved. Otherwise do nothing.
     * @param requestId id of the certificate signing request
     * @param approvedBy authority (its identifier) approving this request.
     * @return True if the request has been approved and false otherwise.
     */
    // TODO: Merge status changing methods.
    fun approveRequest(requestId: String, approvedBy: String): Boolean

    /**
     * Reject the given request using the given reason.
     * @param requestId id of the certificate signing request
     * @param rejectBy authority (its identifier) rejecting this request.
     * @param rejectReason brief description of the rejection reason
     */
    fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String)

    /**
     * Store certificate path with [requestId], this will store the encoded [CertPath] and transit request statue to [RequestStatus.Signed].
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