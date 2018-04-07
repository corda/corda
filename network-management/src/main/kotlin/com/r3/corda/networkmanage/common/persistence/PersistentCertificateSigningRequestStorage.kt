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
            val request = uniqueEntityWhere<CertificateSigningRequestEntity> { builder, path ->
                val requestIdEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::requestId.name), requestId)
                val statusEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::status.name), RequestStatus.APPROVED)
                builder.and(requestIdEq, statusEq)
            }
            request ?: throw IllegalArgumentException("Cannot retrieve 'APPROVED' certificate signing request for request id: $requestId")
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
        val requestId = SecureHash.randomSHA256().toString()
        database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val legalNameOrRejectMessage = try {
                validateRequestAndParseLegalName(request)
            } catch (e: RequestValidationException) {
                e.rejectMessage
            }
            val requestEntity = CertificateSigningRequestEntity(
                    requestId = requestId,
                    legalName = legalNameOrRejectMessage as? CordaX500Name,
                    publicKeyHash = toSupportedPublicKey(request.subjectPublicKeyInfo).hash,
                    request = request,
                    remark = legalNameOrRejectMessage as? String,
                    modifiedBy = CertificateSigningRequestStorage.DOORMAN_SIGNATURE,
                    status = if (legalNameOrRejectMessage is CordaX500Name) RequestStatus.NEW else RequestStatus.REJECTED
            )
            session.save(requestEntity)
        }
        return requestId
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
                        modifiedBy = approvedBy,
                        modifiedAt = Instant.now(),
                        status = RequestStatus.APPROVED)
                session.merge(update)
            }
        }
    }

    override fun rejectRequest(requestId: String, rejectedBy: String, rejectReason: String?) {
        database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val request = findRequest(requestId)
            request ?: throw IllegalArgumentException("Error when rejecting request with id: $requestId. Request does not exist.")
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

    private fun DatabaseTransaction.validateRequestAndParseLegalName(request: PKCS10CertificationRequest): CordaX500Name {
        // It's important that we always use the toString() output of CordaX500Name as it standardises the string format
        // to make querying possible.
        val legalName = try {
            CordaX500Name.build(X500Principal(request.subject.encoded))
        } catch (e: IllegalArgumentException) {
            throw RequestValidationException(request.subject.toString(), "Name validation failed: ${e.message}")
        }
        return when {
        // Check if requested role is valid.
            request.getCertRole() !in allowedCertRoles -> throw RequestValidationException(legalName.toString(), "Requested certificate role ${request.getCertRole()} is not allowed.")
        // TODO consider scenario: There is a CSR that is signed but the certificate itself has expired or was revoked
        // Also, at the moment we assume that once the CSR is approved it cannot be rejected.
        // What if we approved something by mistake.
            nonRejectedRequestExists(CertificateSigningRequestEntity::legalName.name, legalName) -> throw RequestValidationException(legalName.toString(), "Duplicate legal name")
        //TODO Consider following scenario: There is a CSR that is signed but the certificate itself has expired or was revoked
            nonRejectedRequestExists(CertificateSigningRequestEntity::publicKeyHash.name, toSupportedPublicKey(request.subjectPublicKeyInfo).hash) -> throw RequestValidationException(legalName.toString(), "Duplicate public key")
            else -> legalName
        }
    }

    /**
     * Check if "non-rejected" request exists with provided column and value.
     */
    private fun DatabaseTransaction.nonRejectedRequestExists(columnName: String, value: Any): Boolean {
        val query = session.criteriaBuilder.run {
            val criteriaQuery = createQuery(CertificateSigningRequestEntity::class.java)
            criteriaQuery.from(CertificateSigningRequestEntity::class.java).run {
                val valueQuery = equal(get<CordaX500Name>(columnName), value)
                val statusQuery = notEqual(get<RequestStatus>(CertificateSigningRequestEntity::status.name), RequestStatus.REJECTED)
                criteriaQuery.where(and(valueQuery, statusQuery))
            }
        }
        return session.createQuery(query).setMaxResults(1).resultList.isNotEmpty()
    }

    private class RequestValidationException(subjectName: String, val rejectMessage: String) : Exception("Validation failed for $subjectName. $rejectMessage.")
}