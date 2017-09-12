package com.r3.corda.doorman.persistence

import com.r3.corda.doorman.CertificateUtilities
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.node.utilities.CordaPersistence
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.Certificate
import java.time.Instant
import javax.persistence.*
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate

// TODO Relax the uniqueness requirement to be on the entire X.500 subject rather than just the legal name
class DBCertificateRequestStorage(private val database: CordaPersistence) : CertificationRequestStorage {
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

            @Column(name = "request_timestamp")
            var requestTimestamp: Instant = Instant.now(),

            @Column(name = "process_timestamp", nullable = true)
            var processTimestamp: Instant? = null,

            @Lob
            @Column(nullable = true)
            var certificate: ByteArray? = null,

            @Column(name = "reject_reason", length = 256, nullable = true)
            var rejectReason: String? = null
    )

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
                        val certNotNull = isNotNull(get<String>(CertificateSigningRequest::certificate.name))
                        val processTimeIsNull = isNull(get<String>(CertificateSigningRequest::processTimestamp.name))
                        criteriaQuery.where(and(nameEq, or(certNotNull, processTimeIsNull)))
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
            val now = Instant.now()
            val request = CertificateSigningRequest(
                    requestId,
                    certificationData.hostName,
                    certificationData.ipAddress,
                    legalName.toString(),
                    certificationData.request.encoded,
                    now,
                    rejectReason = rejectReason,
                    processTimestamp = rejectReason?.let { now }
            )
            session.save(request)
        }
        return requestId
    }

    override fun getResponse(requestId: String): CertificateResponse {
        return database.transaction {
            val response = singleRequestWhere { builder, path ->
                val eq = builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
                val timeNotNull = builder.isNotNull(path.get<Instant>(CertificateSigningRequest::processTimestamp.name))
                builder.and(eq, timeNotNull)
            }

            if (response == null) {
                CertificateResponse.NotReady
            } else {
                val certificate = response.certificate
                if (certificate != null) {
                    CertificateResponse.Ready(CertificateUtilities.toX509Certificate(certificate))
                } else {
                    CertificateResponse.Unauthorised(response.rejectReason!!)
                }
            }
        }
    }

    override fun approveRequest(requestId: String, generateCertificate: CertificationRequestData.() -> Certificate) {
        database.transaction {
            val request = singleRequestWhere { builder, path ->
                val eq = builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
                val timeIsNull = builder.isNull(path.get<Instant>(CertificateSigningRequest::processTimestamp.name))
                builder.and(eq, timeIsNull)
            }
            if (request != null) {
                request.certificate = request.toRequestData().generateCertificate().encoded
                request.processTimestamp = Instant.now()
                session.save(request)
            }
        }
    }

    override fun rejectRequest(requestId: String, rejectReason: String) {
        database.transaction {
            val request = singleRequestWhere { builder, path ->
                val eq = builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
                val timeIsNull = builder.isNull(path.get<Instant>(CertificateSigningRequest::processTimestamp.name))
                builder.and(eq, timeIsNull)
            }
            if (request != null) {
                request.rejectReason = rejectReason
                request.processTimestamp = Instant.now()
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

    override fun getPendingRequestIds(): List<String> {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(String::class.java).run {
                from(CertificateSigningRequest::class.java).run {
                    select(get(CertificateSigningRequest::requestId.name))
                    where(builder.isNull(get<String>(CertificateSigningRequest::processTimestamp.name)))
                }
            }
            session.createQuery(query).resultList
        }
    }

    override fun getApprovedRequestIds(): List<String> = emptyList()

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
}