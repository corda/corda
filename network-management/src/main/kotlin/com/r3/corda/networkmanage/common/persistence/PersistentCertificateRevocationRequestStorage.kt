package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.CertificateDataEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateRevocationRequestEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateSigningRequestEntity
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import java.math.BigInteger
import java.security.cert.CRLReason
import java.security.cert.X509Certificate
import java.time.Instant

class PersistentCertificateRevocationRequestStorage(private val database: CordaPersistence) : CertificateRevocationRequestStorage {
    private companion object {
        val ALLOWED_REASONS = arrayOf(
                CRLReason.KEY_COMPROMISE,
                CRLReason.AFFILIATION_CHANGED,
                CRLReason.CA_COMPROMISE,
                CRLReason.CESSATION_OF_OPERATION,
                CRLReason.PRIVILEGE_WITHDRAWN,
                CRLReason.SUPERSEDED,
                CRLReason.UNSPECIFIED
        )
        val logger = contextLogger()
    }

    override fun saveRevocationRequest(request: CertificateRevocationRequest): String {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            // Get matching CSR
            validate(request)
            val csr = requireNotNull(retrieveCsr(request.certificateSerialNumber, request.csrRequestId, request.legalName)) {
                "No CSR matching the given criteria was found"
            }
            // Check if there is an entry for the given certificate serial number
            val revocation = uniqueEntityWhere<CertificateRevocationRequestEntity> { builder, path ->
                val serialNumberEqual = builder.equal(path.get<BigInteger>(CertificateRevocationRequestEntity::certificateSerialNumber.name), request.certificateSerialNumber)
                val statusNotEqualRejected = builder.notEqual(path.get<RequestStatus>(CertificateRevocationRequestEntity::status.name), RequestStatus.REJECTED)
                builder.and(serialNumberEqual, statusNotEqualRejected)
            }
            if (revocation != null) {
                revocation.requestId
            } else {
                val certificateData = csr.certificateData!!
                requireNotNull(certificateData) { "The certificate with the given serial number cannot be found." }
                val requestId = SecureHash.randomSHA256().toString()
                session.save(CertificateRevocationRequestEntity(
                        certificateSerialNumber = certificateData.certificateSerialNumber,
                        revocationReason = request.reason,
                        requestId = requestId,
                        modifiedBy = request.reporter,
                        certificateData = certificateData,
                        reporter = request.reporter,
                        legalName = certificateData.legalName
                ))
                requestId
            }
        }
    }

    private fun validate(request: CertificateRevocationRequest) {
        require(request.reason in ALLOWED_REASONS) { "The given revocation reason is not allowed." }
    }

    private fun DatabaseTransaction.retrieveCsr(certificateSerialNumber: BigInteger?, csrRequestId: String?, legalName: CordaX500Name?): CertificateSigningRequestEntity? {
        val csr = if (csrRequestId != null) {
            uniqueEntityWhere<CertificateSigningRequestEntity> { builder, path ->
                builder.and(
                        builder.equal(path.get<String>(CertificateSigningRequestEntity::requestId.name), csrRequestId),
                        builder.notEqual(path.get<RequestStatus>(CertificateSigningRequestEntity::status.name), RequestStatus.REJECTED),
                        builder.equal(path
                                .get<CertificateDataEntity>(CertificateSigningRequestEntity::certificateData.name)
                                .get<CertificateStatus>(CertificateDataEntity::certificateStatus.name), CertificateStatus.VALID)
                )
            }
        } else if (legalName != null) {
            uniqueEntityWhere<CertificateSigningRequestEntity> { builder, path ->
                builder.and(
                        builder.equal(path.get<String>(CertificateSigningRequestEntity::legalName.name), legalName.toString()),
                        builder.notEqual(path.get<RequestStatus>(CertificateSigningRequestEntity::status.name), RequestStatus.REJECTED),
                        builder.equal(path
                                .get<CertificateDataEntity>(CertificateSigningRequestEntity::certificateData.name)
                                .get<CertificateStatus>(CertificateDataEntity::certificateStatus.name), CertificateStatus.VALID)
                )
            }
        } else {
            val certificateData = uniqueEntityWhere<CertificateDataEntity> { builder, path ->
                builder.and(
                        builder.equal(path.get<CertificateStatus>(CertificateDataEntity::certificateStatus.name), CertificateStatus.VALID),
                        builder.equal(path.get<String>(CertificateDataEntity::certificateSerialNumber.name), certificateSerialNumber)
                )
            }
            certificateData?.certificateSigningRequest
        }
        return csr?.let {
            validateCsrConsistency(certificateSerialNumber, csrRequestId, legalName, it)
        }
    }

    private fun validateCsrConsistency(certificateSerialNumber: BigInteger?,
                                       csrRequestId: String?,
                                       legalName: CordaX500Name?,
                                       result: CertificateSigningRequestEntity): CertificateSigningRequestEntity {
        val certData = result.certificateData!!
        require(legalName == null || result.legalName == legalName) {
            "The legal name does not match."
        }
        require(csrRequestId == null || result.requestId == csrRequestId) {
            "The CSR request ID does not match."
        }
        require(certificateSerialNumber == null || certData.certificateSerialNumber == certificateSerialNumber) {
            "The certificate serial number does not match."
        }
        return result
    }

    override fun getRevocationRequest(requestId: String): CertificateRevocationRequestData? {
        return database.transaction {
            getRevocationRequestEntity(requestId)?.toCertificateRevocationRequestData()
        }
    }

    override fun getRevocationRequests(revocationStatus: RequestStatus): List<CertificateRevocationRequestData> {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(CertificateRevocationRequestEntity::class.java).run {
                from(CertificateRevocationRequestEntity::class.java).run {
                    where(builder.equal(get<RequestStatus>(CertificateRevocationRequestEntity::status.name), revocationStatus))
                }
            }
            session.createQuery(query).resultList.map { it.toCertificateRevocationRequestData() }
        }
    }

    override fun approveRevocationRequest(requestId: String, approvedBy: String) {
        database.transaction {
            val revocation = getRevocationRequestEntity(requestId)
            if (revocation == null) {
                throw NoSuchElementException("Error while approving! Certificate revocation id=$id does not exist")
            } else {
                when (revocation.status) {
                    RequestStatus.TICKET_CREATED -> {
                        session.merge(revocation.copy(
                                status = RequestStatus.APPROVED,
                                modifiedAt = Instant.now(),
                                modifiedBy = approvedBy
                        ))
                        logger.debug("`request id` = $requestId marked as APPROVED")
                    }
                    else -> {
                        logger.warn("`request id` = $requestId cannot be marked as APPROVED. Its current status is ${revocation.status}")
                        return@transaction
                    }
                }
            }
        }
    }

    override fun rejectRevocationRequest(requestId: String, rejectedBy: String, reason: String?) {
        database.transaction {
            val revocation = getRevocationRequestEntity(requestId)
            if (revocation == null) {
                throw NoSuchElementException("Error while rejecting! Certificate revocation id=$id does not exist")
            } else {
                when (revocation.status) {
                    RequestStatus.TICKET_CREATED -> {
                        session.merge(revocation.copy(
                                status = RequestStatus.REJECTED,
                                modifiedAt = Instant.now(),
                                modifiedBy = rejectedBy,
                                remark = reason
                        ))
                        logger.debug("`request id` = $requestId marked as REJECTED")
                    }
                    else -> {
                        logger.warn("`request id` = $requestId cannot be marked as REJECTED. Its current status is ${revocation.status}")
                        return@transaction
                    }
                }
            }
        }
    }

    override fun markRequestTicketCreated(requestId: String) {
        database.transaction {
            val revocation = getRevocationRequestEntity(requestId)
            if (revocation == null) {
                throw NoSuchElementException("Error while marking the request as ticket created! Certificate revocation id=$id does not exist")
            } else {
                when (revocation.status) {
                    RequestStatus.NEW -> {
                        session.merge(revocation.copy(
                                modifiedAt = Instant.now(),
                                status = RequestStatus.TICKET_CREATED
                        ))
                        logger.debug("`request id` = $requestId marked as TICKED_CREATED")
                    }
                    else -> {
                        logger.warn("`request id` = $requestId cannot be marked as TICKED_CREATED. Its current status is ${revocation.status}")
                        return@transaction
                    }
                }
            }
        }
    }

    private fun getRevocationRequestEntity(requestId: String, status: RequestStatus? = null): CertificateRevocationRequestEntity? {
        return database.transaction {
            uniqueEntityWhere { builder, path ->
                val idEqual = builder.equal(path.get<String>(CertificateRevocationRequestEntity::requestId.name), requestId)
                if (status == null) {
                    idEqual
                } else {
                    builder.and(idEqual, builder.equal(path.get<RequestStatus>(CertificateRevocationRequestEntity::status.name), status))
                }
            }
        }
    }

    private fun CertificateRevocationRequestEntity.toCertificateRevocationRequestData(): CertificateRevocationRequestData {
        return CertificateRevocationRequestData(
                requestId,
                certificateData.certificateSigningRequest.requestId,
                certificateData.certPath.certificates.first() as X509Certificate,
                certificateSerialNumber,
                modifiedAt,
                legalName,
                status,
                revocationReason,
                reporter
        )
    }
}
