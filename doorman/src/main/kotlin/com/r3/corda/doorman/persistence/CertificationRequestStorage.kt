package com.r3.corda.doorman.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest
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
     *
     * @return True if the request has been approved and false otherwise.
     */
    // TODO: Merge status changing methods.
    fun approveRequest(requestId: String, approvedBy: String = DOORMAN_SIGNATURE): Boolean

    /**
     * Reject the given request using the given reason.
     */
    fun rejectRequest(requestId: String, rejectedBy: String = DOORMAN_SIGNATURE, rejectReason: String)

    /**
     * Store certificate path with [requestId], this will store the encoded [CertPath] and transit request statue to [RequestStatus.Signed].
     *
     * @throws IllegalArgumentException if request is not found or not in Approved state.
     */
    fun putCertificatePath(requestId: String, certificates: CertPath, signedBy: List<String> = listOf(DOORMAN_SIGNATURE))
}

@Entity
@Table(name = "certificate_signing_request", indexes = arrayOf(Index(name = "IDX_PUB_KEY_HASH", columnList = "public_key_hash")))
// TODO: Use Hibernate Envers to audit the table instead of individual "changed_by"/"changed_at" columns.
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

        @Column(name = "created_at")
        var createdAt: Instant = Instant.now(),

        @Column(name = "approved_at")
        var approvedAt: Instant = Instant.now(),

        @Column(name = "approved_by", length = 64)
        var approvedBy: String? = null,

        @Column
        @Enumerated(EnumType.STRING)
        var status: RequestStatus = RequestStatus.New,

        @Column(name = "signed_by", length = 512)
        @ElementCollection(targetClass = String::class, fetch = FetchType.EAGER)
        var signedBy: List<String>? = null,

        @Column(name = "signed_at")
        var signedAt: Instant? = Instant.now(),

        @Column(name = "rejected_by", length = 64)
        var rejectedBy: String? = null,

        @Column(name = "rejected_at")
        var rejectedAt: Instant? = Instant.now(),

        @Column(name = "reject_reason", length = 256, nullable = true)
        var rejectReason: String? = null,

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