package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.CertificateDataEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateSigningRequestEntity
import com.r3.corda.networkmanage.common.utils.hashString
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.hibernate.Session
import java.security.cert.CertPath
import java.time.Instant
import javax.security.auth.x500.X500Principal

/**
 * Database implementation of the [CertificationRequestStorage] interface.
 */
class PersistentCertificateRequestStorage(private val database: CordaPersistence) : CertificationRequestStorage {
    override fun putCertificatePath(requestId: String, certificates: CertPath, signedBy: List<String>) {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val request = singleRequestWhere(CertificateSigningRequestEntity::class.java) { builder, path ->
                val requestIdEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::requestId.name), requestId)
                val statusEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::status.name), RequestStatus.APPROVED)
                builder.and(requestIdEq, statusEq)
            }
            request ?: throw IllegalArgumentException("Cannot retrieve 'APPROVED' certificate signing request for request id: $requestId")
            val publicKeyHash = certificates.certificates.first().publicKey.hashString()
            val certificateSigningRequest = request.copy(
                    modifiedBy = signedBy,
                    modifiedAt = Instant.now(),
                    status = RequestStatus.SIGNED)
            session.merge(certificateSigningRequest)
            val certificateDataEntity = CertificateDataEntity(
                    publicKeyHash = publicKeyHash,
                    certificateStatus = CertificateStatus.VALID,
                    certificatePathBytes = certificates.encoded,
                    certificateSigningRequest = certificateSigningRequest)
            session.persist(certificateDataEntity)
        }
    }

    override fun saveRequest(request: PKCS10CertificationRequest): String {
        val requestId = SecureHash.randomSHA256().toString()
        database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val (legalName, rejectReason) = parseAndValidateLegalName(request, session)
            session.save(CertificateSigningRequestEntity(
                    requestId = requestId,
                    legalName = legalName,
                    requestBytes = request.encoded,
                    remark = rejectReason,
                    modifiedBy = emptyList(),
                    status = if (rejectReason == null) RequestStatus.NEW else RequestStatus.REJECTED
            ))
        }
        return requestId
    }

    private fun DatabaseTransaction.findRequest(requestId: String,
                                                requestStatus: RequestStatus? = null): CertificateSigningRequestEntity? {
        return singleRequestWhere(CertificateSigningRequestEntity::class.java) { builder, path ->
            val idClause = builder.equal(path.get<String>(CertificateSigningRequestEntity::requestId.name), requestId)
            if (requestStatus == null) {
                idClause
            } else {
                val statusClause = builder.equal(path.get<String>(CertificateSigningRequestEntity::status.name), requestStatus)
                builder.and(idClause, statusClause)
            }
        }
    }

    override fun markRequestTicketCreated(requestId: String) {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val request = findRequest(requestId, RequestStatus.NEW)
            request ?: throw IllegalArgumentException("Error when creating request ticket with id: $requestId. Request does not exist or its status is not NEW.")
            val update = request.copy(
                    modifiedAt = Instant.now(),
                    status = RequestStatus.TICKET_CREATED)
            session.merge(update)
        }
    }

    override fun approveRequest(requestId: String, approvedBy: String) {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            findRequest(requestId, RequestStatus.TICKET_CREATED)?.let {
                val update = it.copy(
                        modifiedBy = listOf(approvedBy),
                        modifiedAt = Instant.now(),
                        status = RequestStatus.APPROVED)
                session.merge(update)
            }
        }
    }

    override fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String) {
        database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val request = findRequest(requestId)
            request ?: throw IllegalArgumentException("Error when rejecting request with id: $requestId. Request does not exist.")
            val update = request.copy(
                    modifiedBy = listOf(rejectedBy),
                    modifiedAt = Instant.now(),
                    status = RequestStatus.REJECTED,
                    remark = rejectReason
            )
            session.merge(update)
        }
    }

    override fun getRequest(requestId: String): CertificateSigningRequest? {
        return database.transaction {
            findRequest(requestId)?.toCertificateSigningRequest()
        }
    }

    override fun getRequests(requestStatus: RequestStatus): List<CertificateSigningRequest> {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(CertificateSigningRequestEntity::class.java).run {
                from(CertificateSigningRequestEntity::class.java).run {
                    where(builder.equal(get<RequestStatus>(CertificateSigningRequestEntity::status.name), requestStatus))
                }
            }
            session.createQuery(query).resultList.map { it.toCertificateSigningRequest() }
        }
    }

    private fun parseAndValidateLegalName(request: PKCS10CertificationRequest, session: Session): Pair<String, String?> {
        // It's important that we always use the toString() output of CordaX500Name as it standardises the string format
        // to make querying possible.
        val legalName = try {
            CordaX500Name.build(X500Principal(request.subject.encoded)).toString()
        } catch (e: IllegalArgumentException) {
            return Pair(request.subject.toString(), "Name validation failed: ${e.message}")
        }

        val query = session.criteriaBuilder.run {
            val criteriaQuery = createQuery(CertificateSigningRequestEntity::class.java)
            criteriaQuery.from(CertificateSigningRequestEntity::class.java).run {
                criteriaQuery.where(equal(get<String>(CertificateSigningRequestEntity::legalName.name), legalName))
            }
        }

        val duplicates = session.createQuery(query).resultList.filter {
            it.status in setOf(RequestStatus.NEW, RequestStatus.TICKET_CREATED, RequestStatus.APPROVED) ||
                    it.certificateData?.certificateStatus == CertificateStatus.VALID
        }

        return Pair(legalName, if (duplicates.isEmpty()) null else "Duplicate legal name")
    }
}