package com.r3.corda.doorman.persistence

import com.r3.corda.doorman.hash
import com.r3.corda.doorman.persistence.RequestStatus.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.x500Name
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.DatabaseTransaction
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.security.cert.CertPath
import java.sql.Connection
import java.time.Instant
import javax.persistence.LockModeType
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate

class DBCertificateRequestStorage(private val database: CordaPersistence) : CertificationRequestStorage {
    override fun putCertificatePath(requestId: String, certificates: CertPath, signedBy: List<String>) {
        return database.transaction(Connection.TRANSACTION_SERIALIZABLE) {
            val request = singleRequestWhere { builder, path ->
                val requestIdEq = builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
                val statusEq = builder.equal(path.get<String>(CertificateSigningRequest::status.name), Approved)
                builder.and(requestIdEq, statusEq)
            }
            require(request != null) { "Cannot retrieve 'APPROVED' certificate signing request for request id: $requestId" }

            val publicKeyHash = certificates.certificates.first().publicKey.hash()
            request!!.certificateData = CertificateData(publicKeyHash, certificates.encoded, CertificateStatus.VALID)
            request.status = Signed
            request.signedBy = signedBy
            request.signedAt = Instant.now()
            session.save(request)
        }
    }

    override fun saveRequest(rawRequest: PKCS10CertificationRequest): String {
        val request = JcaPKCS10CertificationRequest(rawRequest)
        val requestId = SecureHash.randomSHA256().toString()
        database.transaction(Connection.TRANSACTION_SERIALIZABLE) {
            // TODO ensure public key not duplicated.
            val (legalName, rejectReason) = try {
                // This will fail with IllegalArgumentException if subject name is malformed.
                val legalName = CordaX500Name.parse(request.subject.toString()).copy(commonName = null)
                // Checks database for duplicate name.
                val query = session.criteriaBuilder.run {
                    val criteriaQuery = createQuery(CertificateSigningRequest::class.java)
                    criteriaQuery.from(CertificateSigningRequest::class.java).run {
                        val nameEq = equal(get<String>(CertificateSigningRequest::legalName.name), legalName.toString())
                        val statusNewOrApproved = get<String>(CertificateSigningRequest::status.name).`in`(Approved, New)
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
                Pair(request.subject, "Name validation failed with exception : ${e.message}")
            }
            session.save(CertificateSigningRequest(
                    requestId = requestId,
                    legalName = legalName.toString(),
                    request = request.encoded,
                    rejectReason = rejectReason,
                    status = if (rejectReason == null) New else Rejected
            ))
        }
        return requestId
    }

    override fun approveRequest(requestId: String, approvedBy: String): Boolean {
        var approved = false
        database.transaction(Connection.TRANSACTION_SERIALIZABLE) {
            val request = singleRequestWhere { builder, path ->
                builder.and(builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId),
                        builder.equal(path.get<String>(CertificateSigningRequest::status.name), New))
            }
            if (request != null) {
                request.approvedAt = Instant.now()
                request.approvedBy = approvedBy
                request.status = Approved
                session.save(request)
                approved = true
            }
        }
        return approved
    }

    override fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String) {
        database.transaction(Connection.TRANSACTION_SERIALIZABLE) {
            val request = singleRequestWhere { builder, path ->
                builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
            }
            if (request != null) {
                request.rejectReason = rejectReason
                request.status = Rejected
                request.rejectedBy = rejectedBy
                request.rejectedAt = Instant.now()
                session.save(request)
            }
        }
    }

    override fun getRequest(requestId: String): CertificateSigningRequest? {
        return database.transaction {
            singleRequestWhere { builder, path ->
                builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
            }
        }
    }

    override fun getRequests(requestStatus: RequestStatus): List<CertificateSigningRequest> {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(CertificateSigningRequest::class.java).run {
                from(CertificateSigningRequest::class.java).run {
                    where(builder.equal(get<RequestStatus>(CertificateSigningRequest::status.name), requestStatus))
                }
            }
            session.createQuery(query).resultList
        }
    }

    private fun DatabaseTransaction.singleRequestWhere(predicate: (CriteriaBuilder, Path<CertificateSigningRequest>) -> Predicate): CertificateSigningRequest? {
        val builder = session.criteriaBuilder
        val criteriaQuery = builder.createQuery(CertificateSigningRequest::class.java)
        val query = criteriaQuery.from(CertificateSigningRequest::class.java).run {
            criteriaQuery.where(predicate(builder, this))
        }
        return session.createQuery(query).setLockMode(LockModeType.PESSIMISTIC_WRITE).uniqueResultOptional().orElse(null)
    }
}