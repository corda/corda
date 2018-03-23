/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.CertPath

data class CertificateData(val certStatus: CertificateStatus, val certPath: CertPath)

data class CertificateSigningRequest(val requestId: String,
                                     val legalName: CordaX500Name?,
                                     val publicKeyHash: SecureHash,
                                     val status: RequestStatus,
                                     val request: PKCS10CertificationRequest,
                                     val remark: String?,
                                     val modifiedBy: String,
                                     val certData: CertificateData?)

/**
 *  Provide certificate signing request storage for the certificate signing server.
 */
interface CertificateSigningRequestStorage {
    companion object {
        val DOORMAN_SIGNATURE = "Doorman-Csr-Signer"
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
     * Persist the fact that a ticket has been created for the given [requestId].
     */
    fun markRequestTicketCreated(requestId: String)

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
    fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String?)

    /**
     * Store certificate path with [requestId], this will store the encoded [CertPath] and transit request status to [RequestStatus.DONE].
     * @param requestId id of the certificate signing request
     * @param certPath chain of certificates starting with the one generated in response to the CSR up to the root.
     * @param signedBy authority (its identifier) signing this request.
     * @throws IllegalArgumentException if request is not found or not in Approved state.
     */
    fun putCertificatePath(requestId: String, certPath: CertPath, signedBy: String)
}

sealed class CertificateResponse {
    object NotReady : CertificateResponse()
    data class Ready(val certificatePath: CertPath) : CertificateResponse()
    data class Unauthorised(val message: String) : CertificateResponse()
}

@CordaSerializable
enum class RequestStatus {
    /**
     * The request has been received, this is the initial state in which a request has been created.
     */
    NEW,

    /**
     * A ticket has been created but has not yet been approved nor rejected.
     */
    TICKET_CREATED,

    /**
     * The request has been approved, but not yet signed.
     */
    APPROVED,

    /**
     * The request has been rejected, this is a terminal state, once a request gets in this state it won't change anymore.
     */
    REJECTED,

    /**
     * The request has been successfully processed, this is a terminal state, once a request gets in this state it won't change anymore.
     */
    DONE
}

enum class CertificateStatus {
    VALID, SUSPENDED, REVOKED
}