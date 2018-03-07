package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.persistence.RequestStatus
import org.hibernate.envers.Audited
import java.math.BigInteger
import java.security.cert.CRLReason
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "certificate_revocation_request")
class CertificateRevocationRequestEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        @Column(name = "request_id", length = 256, nullable = false, unique = true)
        val requestId: String,

        @OneToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "certificate_data")
        val certificateData: CertificateDataEntity,

        @Column(name = "certificate_serial_number", nullable = false)
        val certificateSerialNumber: BigInteger,

        @Column(name = "legal_name", length = 256, nullable = false)
        val legalName: String,

        @Audited
        @Column(name = "status", nullable = false)
        @Enumerated(EnumType.STRING)
        val status: RequestStatus = RequestStatus.NEW,

        @Column(name = "reporter", nullable = false, length = 512)
        val reporter: String,

        @Audited
        @Column(name = "modified_by", nullable = false, length = 512)
        val modifiedBy: String,

        @Audited
        @Column(name = "modified_at", nullable = false)
        val modifiedAt: Instant = Instant.now(),

        @Audited
        @Column(name = "revocation_reason", nullable = false)
        @Enumerated(EnumType.STRING)
        val revocationReason: CRLReason,

        @Audited
        @Column(name = "remark", length = 256)
        val remark: String? = null
) {
    fun copy(id: Long? = this.id,
             requestId: String = this.requestId,
             certificateData: CertificateDataEntity = this.certificateData,
             certificateSerialNumber: BigInteger = this.certificateSerialNumber,
             status: RequestStatus = this.status,
             legalName: String = this.legalName,
             reporter: String = this.reporter,
             modifiedBy: String = this.modifiedBy,
             modifiedAt: Instant = this.modifiedAt,
             revocationReason: CRLReason = this.revocationReason,
             remark: String? = this.remark): CertificateRevocationRequestEntity {
        return CertificateRevocationRequestEntity(
                id = id,
                requestId = requestId,
                certificateData = certificateData,
                certificateSerialNumber = certificateSerialNumber,
                status = status,
                legalName = legalName,
                reporter = reporter,
                modifiedBy = modifiedBy,
                modifiedAt = modifiedAt,
                revocationReason = revocationReason,
                remark = remark
        )
    }
}