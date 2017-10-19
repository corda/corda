package com.r3.corda.doorman.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.hibernate.envers.Audited
import java.security.cert.CertPath
import java.time.Instant
import javax.persistence.*

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

@Entity
@Table(name = "certificate_signing_request", indexes = arrayOf(Index(name = "IDX_PUB_KEY_HASH", columnList = "public_key_hash")))
class CertificateSigningRequest(
        @Id
        @Column(name = "request_id", length = 64)
        var requestId: String = "",

        // TODO: Store X500Name with a proper schema.
        @Column(name = "legal_name", length = 256)
        var legalName: String = "",

        @Lob
        @Column
        var request: ByteArray = ByteArray(0),

        @Audited
        @Column(name = "status")
        @Enumerated(EnumType.STRING)
        var status: RequestStatus = RequestStatus.New,

        @Audited
        @Column(name = "modified_by", length = 512)
        @ElementCollection(targetClass = String::class, fetch = FetchType.EAGER)
        var modifiedBy: List<String> = emptyList(),

        @Audited
        @Column(name = "modified_at")
        var modifiedAt: Instant? = Instant.now(),

        @Audited
        @Column(name = "remark", length = 256, nullable = true)
        var remark: String? = null,

        // TODO: The certificate data can have its own table.
        @Embedded
        var certificateData: CertificateData? = null
)

@Embeddable
class CertificateData(
        @Column(name = "public_key_hash", length = 64, nullable = true)
        var publicKeyHash: String? = null,

        @Lob
        @Column(nullable = true)
        var certificatePath: ByteArray? = null,

        @Column(name = "certificate_status", nullable = true)
        var certificateStatus: CertificateStatus? = null
)

enum class CertificateStatus {
    VALID, SUSPENDED, REVOKED
}

enum class RequestStatus {
    New, Approved, Rejected, Signed
}

sealed class CertificateResponse {
    object NotReady : CertificateResponse()
    data class Ready(val certificatePath: CertPath) : CertificateResponse()
    data class Unauthorised(val message: String) : CertificateResponse()
}