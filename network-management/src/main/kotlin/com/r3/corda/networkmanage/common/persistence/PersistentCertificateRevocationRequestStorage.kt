package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.CertificateDataEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateRevocationRequestEntity
import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import java.math.BigInteger
import java.security.cert.CRLReason
import java.time.Instant

class PersistentCertificateRevocationRequestStorage(private val database: CordaPersistence) : CertificateRevocationRequestStorage {
    override fun saveRevocationRequest(certificateSerialNumber: BigInteger, reason: CRLReason, reporter: String): String {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            // Check if there is an entry for the given certificate serial number
            val revocation = uniqueEntityWhere<CertificateRevocationRequestEntity> { builder, path ->
                val serialNumberEqual = builder.equal(path.get<BigInteger>(CertificateRevocationRequestEntity::certificateSerialNumber.name), certificateSerialNumber)
                val statusNotEqualRejected = builder.notEqual(path.get<RequestStatus>(CertificateRevocationRequestEntity::status.name), RequestStatus.REJECTED)
                builder.and(serialNumberEqual, statusNotEqualRejected)
            }
            if (revocation != null) {
                revocation.requestId
            } else {
                val certificateData = uniqueEntityWhere<CertificateDataEntity> { builder, path ->
                    val serialNumberEqual = builder.equal(path.get<BigInteger>(CertificateDataEntity::certificateSerialNumber.name), certificateSerialNumber)
                    val statusEqualValid = builder.equal(path.get<CertificateStatus>(CertificateDataEntity::certificateStatus.name), CertificateStatus.VALID)
                    builder.and(serialNumberEqual, statusEqualValid)
                }
                requireNotNull(certificateData) { "The certificate with the given serial number cannot be found." }
                val requestId = SecureHash.randomSHA256().toString()
                session.save(CertificateRevocationRequestEntity(
                        certificateSerialNumber = certificateSerialNumber,
                        revocationReason = reason,
                        requestId = requestId,
                        modifiedBy = reporter,
                        certificateData = certificateData!!,
                        reporter = reporter,
                        legalName = certificateData.legalName()
                ))
                requestId
            }
        }
    }

    override fun getRevocationRequest(requestId: String): CertificateRevocationRequestData? = database.transaction {
        getRevocationRequestEntity(requestId)?.toCertificateRevocationRequest()
    }

    override fun getRevocationRequests(revocationStatus: RequestStatus): List<CertificateRevocationRequestData> {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(CertificateRevocationRequestEntity::class.java).run {
                from(CertificateRevocationRequestEntity::class.java).run {
                    where(builder.equal(get<RequestStatus>(CertificateRevocationRequestEntity::status.name), revocationStatus))
                }
            }
            session.createQuery(query).resultList.map { it.toCertificateRevocationRequest() }
        }
    }

    override fun approveRevocationRequest(requestId: String, approvedBy: String) {
        database.transaction {
            val revocation = getRevocationRequestEntity(requestId)
            if (revocation == null) {
                throw NoSuchElementException("Error while approving! Certificate revocation id=$id does not exist")
            } else {
                session.merge(revocation.copy(
                        status = RequestStatus.APPROVED,
                        modifiedAt = Instant.now(),
                        modifiedBy = approvedBy
                ))
            }
        }
    }

    override fun rejectRevocationRequest(requestId: String, rejectedBy: String, reason: String) {
        database.transaction {
            val revocation = getRevocationRequestEntity(requestId)
            if (revocation == null) {
                throw NoSuchElementException("Error while rejecting! Certificate revocation id=$id does not exist")
            } else {
                session.merge(revocation.copy(
                        status = RequestStatus.REJECTED,
                        modifiedAt = Instant.now(),
                        modifiedBy = rejectedBy,
                        remark = reason
                ))
            }
        }
    }

    private fun getRevocationRequestEntity(requestId: String): CertificateRevocationRequestEntity? = database.transaction {
        uniqueEntityWhere { builder, path ->
            builder.equal(path.get<String>(CertificateRevocationRequestEntity::requestId.name), requestId)
        }
    }

    private fun CertificateRevocationRequestEntity.toCertificateRevocationRequest(): CertificateRevocationRequestData {
        return CertificateRevocationRequestData(
                requestId,
                certificateSerialNumber,
                if (status == RequestStatus.DONE) modifiedAt else null,
                legalName,
                status,
                revocationReason,
                reporter)
    }
}