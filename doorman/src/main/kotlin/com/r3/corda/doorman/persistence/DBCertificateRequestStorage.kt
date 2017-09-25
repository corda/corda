package com.r3.corda.doorman.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.node.utilities.CordaPersistence
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayInputStream
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.time.Instant
import javax.persistence.*
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate

// TODO Relax the uniqueness requirement to be on the entire X.500 subject rather than just the legal name
open class DBCertificateRequestStorage(private val database: CordaPersistence) : CertificationRequestStorage {
    @Entity
    @Table(name = "certificate_signing_request")
    class CertificateSigningRequest(
            @Id
            @Column(name = "request_id", length = 64)
            var requestId: String = "",

            @Column(name = "host_name", length = 100)
            var hostName: String = "",

            @Column(name = "ip_address", length = 15)
            var ipAddress: String = "",

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
            var status: Status = Status.New,

            @Column(name = "signed_by", length = 512)
            @ElementCollection(targetClass = String::class, fetch = FetchType.EAGER)
            var signedBy: List<String>? = null,

            @Column(name = "signed_at")
            var signedAt: Instant? = Instant.now(),

            @Column(name = "rejected_by", length = 64)
            var rejectedBy: String? = null,

            @Column(name = "rejected_at")
            var rejectedAt: Instant? = Instant.now(),

            @Lob
            @Column(nullable = true)
            var certificatePath: ByteArray? = null,

            @Column(name = "reject_reason", length = 256, nullable = true)
            var rejectReason: String? = null
    )

    enum class Status {
        New, Approved, Rejected, Signed
    }

    override fun saveRequest(certificationData: CertificationRequestData): String {
        val requestId = SecureHash.randomSHA256().toString()

        database.transaction {
            val (legalName, rejectReason) = try {
                // This will fail with IllegalArgumentException if subject name is malformed.
                val legalName = CordaX500Name.build(certificationData.request.subject).copy(commonName = null)
                // Checks database for duplicate name.
                val query = session.criteriaBuilder.run {
                    val criteriaQuery = createQuery(CertificateSigningRequest::class.java)
                    criteriaQuery.from(CertificateSigningRequest::class.java).run {
                        val nameEq = equal(get<String>(CertificateSigningRequest::legalName.name), legalName.toString())
                        val statusNewOrApproved = get<String>(CertificateSigningRequest::status.name).`in`(Status.Approved, Status.New)
                        criteriaQuery.where(and(nameEq, statusNewOrApproved))
                    }
                }
                val duplicate = session.createQuery(query).resultList.isNotEmpty()
                if (duplicate) {
                    Pair(legalName.x500Name, "Duplicate legal name")
                } else {
                    Pair(legalName.x500Name, null)
                }
            } catch (e: IllegalArgumentException) {
                Pair(certificationData.request.subject, "Name validation failed with exception : ${e.message}")
            }
            val request = CertificateSigningRequest(
                    requestId = requestId,
                    hostName = certificationData.hostName,
                    ipAddress = certificationData.ipAddress,
                    legalName = legalName.toString(),
                    request = certificationData.request.encoded,
                    rejectReason = rejectReason,
                    status = if (rejectReason == null) Status.New else Status.Rejected
            )
            session.save(request)
        }
        return requestId
    }

    override fun getResponse(requestId: String): CertificateResponse {
        return database.transaction {
            val response = singleRequestWhere { builder, path ->
                builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
            }
            if (response == null) {
                CertificateResponse.NotReady
            } else {
                when (response.status) {
                    Status.New, Status.Approved -> CertificateResponse.NotReady
                    Status.Rejected -> CertificateResponse.Unauthorised(response.rejectReason ?: "Unknown reason")
                    Status.Signed -> CertificateResponse.Ready(buildCertPath(response.certificatePath))
                }
            }
        }
    }

    override fun approveRequest(requestId: String, approvedBy: String): Boolean {
        var approved = false
        database.transaction {
            val request = singleRequestWhere { builder, path ->
                builder.and(builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId),
                        builder.equal(path.get<String>(CertificateSigningRequest::status.name), Status.New))
            }
            if (request != null) {
                request.approvedAt = Instant.now()
                request.approvedBy = approvedBy
                request.status = Status.Approved
                session.save(request)
                approved = true
            }
        }
        return approved
    }

    override fun signCertificate(requestId: String, signedBy: List<String>, generateCertificate: CertificationRequestData.() -> CertPath): Boolean {
        var signed = false
        database.transaction {
            val request = singleRequestWhere { builder, path ->
                builder.and(builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId),
                        builder.equal(path.get<String>(CertificateSigningRequest::status.name), Status.Approved))
            }
            if (request != null) {
                val now = Instant.now()
                request.certificatePath = request.toRequestData().generateCertificate().encoded
                request.status = Status.Signed
                request.signedAt = now
                request.signedBy = signedBy
                session.save(request)
                signed = true
            }
        }
        return signed
    }

    override fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String) {
        database.transaction {
            val request = singleRequestWhere { builder, path ->
                builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
            }
            if (request != null) {
                request.rejectReason = rejectReason
                request.status = Status.Rejected
                request.rejectedBy = rejectedBy
                request.rejectedAt = Instant.now()
                session.save(request)
            }
        }
    }

    override fun getRequest(requestId: String): CertificationRequestData? {
        return database.transaction {
            singleRequestWhere { builder, path ->
                builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
            }
        }?.toRequestData()
    }

    override fun getApprovedRequestIds(): List<String> {
        return getRequestIdsByStatus(Status.Approved)
    }

    override fun getNewRequestIds(): List<String> {
        return getRequestIdsByStatus(Status.New)
    }

    override fun getSignedRequestIds(): List<String> {
        return getRequestIdsByStatus(Status.Signed)
    }

    private fun getRequestIdsByStatus(status: Status): List<String> {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(String::class.java).run {
                from(CertificateSigningRequest::class.java).run {
                    select(get(CertificateSigningRequest::requestId.name))
                    where(builder.equal(get<Status>(CertificateSigningRequest::status.name), status))
                }
            }
            session.createQuery(query).resultList
        }
    }

    private fun singleRequestWhere(predicate: (CriteriaBuilder, Path<CertificateSigningRequest>) -> Predicate): CertificateSigningRequest? {
        return database.transaction {
            val builder = session.criteriaBuilder
            val criteriaQuery = builder.createQuery(CertificateSigningRequest::class.java)
            val query = criteriaQuery.from(CertificateSigningRequest::class.java).run {
                criteriaQuery.where(predicate(builder, this))
            }
            session.createQuery(query).uniqueResultOptional().orElse(null)
        }
    }

    private fun CertificateSigningRequest.toRequestData() = CertificationRequestData(hostName, ipAddress, PKCS10CertificationRequest(request))

    private fun buildCertPath(certPathBytes: ByteArray?) = CertificateFactory.getInstance("X509").generateCertPath(ByteArrayInputStream(certPathBytes))
}