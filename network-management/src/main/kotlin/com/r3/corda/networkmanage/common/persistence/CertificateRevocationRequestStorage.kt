package com.r3.corda.networkmanage.common.persistence

import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import java.math.BigInteger
import java.security.cert.CRLReason
import java.security.cert.X509Certificate
import java.time.Instant

/**
 * This data class is intended to be used internally certificate revocation request service.
 */
@CordaSerializable
data class CertificateRevocationRequestData(val requestId: String, // This is a uniquely generated string
                                            val certificateSigningRequestId: String,
                                            val certificate: X509Certificate,
                                            val certificateSerialNumber: BigInteger,
                                            val modifiedAt: Instant,
                                            val legalName: CordaX500Name,
                                            val status: RequestStatus,
                                            val reason: CRLReason,
                                            val reporter: String) // Username of the reporter

/**
 * Interface for managing certificate revocation requests persistence
 */
interface CertificateRevocationRequestStorage {
    companion object {
        val DOORMAN_SIGNATURE = "Doorman-Crr-Signer"
    }

    /**
     * Creates a new revocation request for the given [certificateSerialNumber].
     * The newly created revocation request has the [RequestStatus.NEW] status.
     * If the revocation request with the [certificateSerialNumber] already exists and has status
     * [RequestStatus.NEW], [RequestStatus.APPROVED] or [RequestStatus.REVOKED]
     * then nothing is persisted and the existing revocation request identifier is returned.
     *
     * @param request certificate revocation request to be stored.
     *
     * @return identifier of the newly created (or existing) revocation request.
     */
    fun saveRevocationRequest(request: CertificateRevocationRequest): String

    /**
     * Retrieves the revocation request with the given [requestId]
     *
     * @param requestId revocation request identifier
     *
     * @return CertificateRevocationRequest matching the specified identifier. Or null if it doesn't exist.
     */
    fun getRevocationRequest(requestId: String): CertificateRevocationRequestData?

    /**
     * Retrieves all the revocation requests with the specified revocation request status.
     *
     * @param revocationStatus revocation request status of the returned revocation requests.
     *
     * @return list of certificate revocation requests that match the revocation request status.
     */
    fun getRevocationRequests(revocationStatus: RequestStatus): List<CertificateRevocationRequestData>

    /**
     * Changes the revocation request status to [RequestStatus.APPROVED].
     *
     * @param requestId revocation request identifier
     * @param approvedBy who is approving it
     */
    fun approveRevocationRequest(requestId: String, approvedBy: String)

    /**
     * Changes the revocation request status to [RequestStatus.REJECTED].
     *
     * @param requestId revocation request identifier
     * @param rejectedBy who is rejecting it
     * @param reason description of the reason of this rejection.
     */
    fun rejectRevocationRequest(requestId: String, rejectedBy: String, reason: String?)

    /**
     * Persist the fact that a ticket has been created for the given [requestId].
     */
    fun markRequestTicketCreated(requestId: String)
}