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

import com.r3.corda.networkmanage.common.persistence.entity.CertificateDataEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateSigningRequestEntity
import com.r3.corda.networkmanage.common.utils.getCertRole
import net.corda.core.crypto.Crypto.toSupportedPublicKey
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.internal.hash
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.PublicKey
import java.security.cert.CertPath
import java.time.Instant
import javax.security.auth.x500.X500Principal

/**
 * Database implementation of the [CertificateSigningRequestStorage] interface.
 */
class PersistentCertificateSigningRequestStorage(private val database: CordaPersistence) : CertificateSigningRequestStorage {
    companion object {
        // TODO: make this configurable?
        private val allowedCertRoles = setOf(CertRole.NODE_CA, CertRole.SERVICE_IDENTITY)
    }

    override fun putCertificatePath(requestId: String, certPath: CertPath, signedBy: String) {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val request = requireNotNull(uniqueEntityWhere<CertificateSigningRequestEntity> { builder, path ->
                val requestIdEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::requestId.name), requestId)
                val statusEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::status.name), RequestStatus.APPROVED)
                builder.and(requestIdEq, statusEq)
            }) { "Cannot retrieve 'APPROVED' certificate signing request for request id: $requestId" }
            val certificateSigningRequest = request.copy(
                    modifiedBy = signedBy,
                    modifiedAt = Instant.now(),
                    status = RequestStatus.DONE)
            session.merge(certificateSigningRequest)
            val certificateDataEntity = CertificateDataEntity(
                    certificateStatus = CertificateStatus.VALID,
                    certPath = certPath,
                    certificateSigningRequest = certificateSigningRequest,
                    certificateSerialNumber = certPath.x509Certificates.first().serialNumber)
            session.persist(certificateDataEntity)
        }
    }

    override fun saveRequest(request: PKCS10CertificationRequest): String {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val (legalName, exception) = try {
                val legalName = parseLegalName(request)
                // Return existing request ID, if request already exists for the same request.
                validateRequest(legalName, request)?.let { return@transaction it }
                Pair(legalName, null)
            } catch (e: RequestValidationException) {
                Pair(e.legalName, e)
            }

            val requestEntity = CertificateSigningRequestEntity(
                    requestId = SecureHash.randomSHA256().toString(),
                    legalName = legalName,
                    publicKeyHash = toSupportedPublicKey(request.subjectPublicKeyInfo).hash,
                    request = request,
                    remark = exception?.rejectMessage,
                    modifiedBy = CertificateSigningRequestStorage.DOORMAN_SIGNATURE,
                    status = if (exception == null) RequestStatus.NEW else RequestStatus.REJECTED
            )
            session.save(requestEntity) as String
        }
    }

    private fun DatabaseTransaction.findRequest(requestId: String,
                                                requestStatus: RequestStatus? = null): CertificateSigningRequestEntity? {
        return uniqueEntityWhere { builder, path ->
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
        database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val request = requireNotNull(findRequest(requestId, RequestStatus.NEW)) { "Error when creating request ticket with id: $requestId. Request does not exist or its status is not NEW." }
            val update = request.copy(
                    modifiedAt = Instant.now(),
                    status = RequestStatus.TICKET_CREATED)
            session.merge(update)
        }
    }

    override fun approveRequest(requestId: String, approvedBy: String) {
        database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            findRequest(requestId, RequestStatus.TICKET_CREATED)?.let {
                val update = it.copy(
                        modifiedBy = approvedBy,
                        modifiedAt = Instant.now(),
                        status = RequestStatus.APPROVED)
                session.merge(update)
            }
        }
    }

    override fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String?) {
        database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val request = requireNotNull(findRequest(requestId)) { "Error when rejecting request with id: $requestId. Request does not exist." }
            val update = request.copy(
                    modifiedBy = rejectedBy,
                    modifiedAt = Instant.now(),
                    status = RequestStatus.REJECTED,
                    remark = rejectReason
            )
            session.merge(update)
        }
    }

    override fun getValidCertificatePath(publicKey: PublicKey): CertPath? {
        return database.transaction {
            session.createQuery(
                    "select a.certificateData.certPath from ${CertificateSigningRequestEntity::class.java.name} a " +
                            "where a.publicKeyHash = :publicKeyHash and a.status = 'DONE' and a.certificateData.certificateStatus = 'VALID'", CertPath::class.java)
                    .setParameter("publicKeyHash", publicKey.hash.toString())
                    .uniqueResult()
        }
    }

    override fun getRequest(requestId: String): CertificateSigningRequest? {
        return database.transaction {
            findRequest(requestId)?.toCertificateSigningRequest()
        }
    }

    override fun getRequests(requestStatus: RequestStatus): List<CertificateSigningRequest> {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(CertificateSigningRequestEntity::class.java).run {
                from(CertificateSigningRequestEntity::class.java).run {
                    where(builder.equal(get<RequestStatus>(CertificateSigningRequestEntity::status.name), requestStatus))
                }
            }
            session.createQuery(query).resultList.map { it.toCertificateSigningRequest() }
        }
    }

    private fun parseLegalName(request: PKCS10CertificationRequest): CordaX500Name {
        return try {
            CordaX500Name.build(X500Principal(request.subject.encoded))
        } catch (e: IllegalArgumentException) {
            throw RequestValidationException(null, request.subject.toString(), rejectMessage = "Name validation failed: ${e.message}")
        }
    }

    /**
     * Validate certificate signing request, returns request ID if same request already exists.
     */
    private fun DatabaseTransaction.validateRequest(legalName: CordaX500Name, request: PKCS10CertificationRequest): String? {
        // Check if the same request exists and returns the request id.
        val existingRequestByPubKeyHash = nonRejectedRequest(CertificateSigningRequestEntity::publicKeyHash.name, toSupportedPublicKey(request.subjectPublicKeyInfo).hash)

        existingRequestByPubKeyHash?.let {
            // Compare subject, attribute.
            // We cannot compare the request directly because it contains nonce.
            if (it.request.subject == request.subject && it.request.attributes.asList() == request.attributes.asList()) {
                return it.requestId
            } else {
                //TODO Consider following scenario: There is a CSR that is signed but the certificate itself has expired or was revoked
                throw RequestValidationException(legalName, rejectMessage = "Duplicate public key")
            }
        }
        // Check if requested role is valid.
        if (request.getCertRole() !in allowedCertRoles)
            throw RequestValidationException(legalName, rejectMessage = "Requested certificate role ${request.getCertRole()} is not allowed.")
        // TODO consider scenario: There is a CSR that is signed but the certificate itself has expired or was revoked
        // Also, at the moment we assume that once the CSR is approved it cannot be rejected.
        // What if we approved something by mistake.
        if (nonRejectedRequest(CertificateSigningRequestEntity::legalName.name, legalName) != null) throw RequestValidationException(legalName, rejectMessage = "Duplicate legal name")

        return null
    }

    /**
     * Retrieve "non-rejected" request which matches provided column and value predicate.
     */
    private fun <T : Any> DatabaseTransaction.nonRejectedRequest(columnName: String, value: T): CertificateSigningRequestEntity? {
        val query = session.criteriaBuilder.run {
            val criteriaQuery = createQuery(CertificateSigningRequestEntity::class.java)
            criteriaQuery.from(CertificateSigningRequestEntity::class.java).run {
                val valueQuery = equal(get<T>(columnName), value)
                val statusQuery = notEqual(get<RequestStatus>(CertificateSigningRequestEntity::status.name), RequestStatus.REJECTED)
                criteriaQuery.where(and(valueQuery, statusQuery))
            }
        }
        return session.createQuery(query).setMaxResults(1).uniqueResult()
    }

    private data class RequestValidationException(val legalName: CordaX500Name?, val subjectName: String = legalName.toString(), val rejectMessage: String) : Exception("Validation failed for $subjectName. $rejectMessage.")
}