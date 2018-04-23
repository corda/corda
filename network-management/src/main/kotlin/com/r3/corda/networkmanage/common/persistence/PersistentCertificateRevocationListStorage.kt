package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.CertificateDataEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateRevocationListEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateRevocationRequestEntity
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.math.BigInteger
import java.security.cert.X509CRL
import java.time.Instant

class PersistentCertificateRevocationListStorage(private val database: CordaPersistence) : CertificateRevocationListStorage {
    override fun getCertificateRevocationList(crlIssuer: CrlIssuer): X509CRL? {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(CertificateRevocationListEntity::class.java).run {
                from(CertificateRevocationListEntity::class.java).run {
                    orderBy(builder.desc(get<String>(CertificateRevocationListEntity::id.name)))
                    where(builder.equal(get<CrlIssuer>(CertificateRevocationListEntity::crlIssuer.name), crlIssuer))
                }
            }
            // We just want the last signed entry
            session.createQuery(query).setMaxResults(1).uniqueResult()?.crl
        }
    }

    override fun saveCertificateRevocationList(crl: X509CRL, crlIssuer: CrlIssuer, signedBy: String, revokedAt: Instant) {
        database.transaction {
            crl.revokedCertificates.forEach {
                revokeCertificate(it.serialNumber, revokedAt, this)
            }
            session.save(CertificateRevocationListEntity(
                    crl = crl,
                    crlIssuer = crlIssuer,
                    signedBy = signedBy,
                    modifiedAt = Instant.now()
            ))
        }
    }

    private fun revokeCertificate(certificateSerialNumber: BigInteger, time: Instant, transaction: DatabaseTransaction) {
        val revocation = transaction.uniqueEntityWhere<CertificateRevocationRequestEntity> { builder, path ->
            builder.and(
                    builder.equal(path.get<BigInteger>(CertificateRevocationRequestEntity::certificateSerialNumber.name), certificateSerialNumber),
                    builder.notEqual(path.get<RequestStatus>(CertificateRevocationRequestEntity::status.name), RequestStatus.REJECTED)
            )
        }
        revocation ?: throw IllegalStateException("The certificate revocation request for $certificateSerialNumber does not exist")
        check(revocation.status in arrayOf(RequestStatus.APPROVED, RequestStatus.DONE)) {
            "The certificate revocation request for $certificateSerialNumber has unexpected status of ${revocation.status}"
        }
        val session = transaction.session
        val certificateData = session.merge(revocation.certificateData.copy(certificateStatus = CertificateStatus.REVOKED)) as CertificateDataEntity
        session.merge(revocation.copy(
                status = RequestStatus.DONE,
                modifiedAt = time,
                certificateData = certificateData
        ))
    }
}