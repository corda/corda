package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.persistence.CertificateData
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.hibernate.envers.Audited
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "certificate_signing_request")
class CertificateSigningRequestEntity(
        @Id
        @Column(name = "request_id", length = 64)
        val requestId: String,

        // TODO: Store X500Name with a proper schema.
        @Column(name = "legal_name", length = 256, nullable = false)
        val legalName: String,

        @Audited
        @Column(name = "status", nullable = false)
        @Enumerated(EnumType.STRING)
        val status: RequestStatus = RequestStatus.NEW,

        @Audited
        @Column(name = "modified_by", length = 512)
        @ElementCollection(targetClass = String::class, fetch = FetchType.EAGER)
        val modifiedBy: List<String> = emptyList(),

        @Audited
        @Column(name = "modified_at", nullable = false)
        val modifiedAt: Instant = Instant.now(),

        @Audited
        @Column(name = "remark", length = 256)
        val remark: String? = null,

        @OneToOne(fetch = FetchType.EAGER, mappedBy = "certificateSigningRequest")
        val certificateData: CertificateDataEntity? = null,

        @Lob
        @Column(name = "request_bytes", nullable = false)
        val requestBytes: ByteArray
) {
    fun toCertificateSigningRequest() = CertificateSigningRequest(
            requestId = requestId,
            legalName = legalName,
            status = status,
            request = request(),
            remark = remark,
            modifiedBy = modifiedBy,
            certData = certificateData?.toCertificateData()
    )

    fun copy(requestId: String = this.requestId,
             legalName: String = this.legalName,
             status: RequestStatus = this.status,
             modifiedBy: List<String> = this.modifiedBy,
             modifiedAt: Instant = this.modifiedAt,
             remark: String? = this.remark,
             certificateData: CertificateDataEntity? = this.certificateData,
             requestBytes: ByteArray = this.requestBytes
    ): CertificateSigningRequestEntity {
        return CertificateSigningRequestEntity(
                requestId = requestId,
                legalName = legalName,
                status = status,
                modifiedAt = modifiedAt,
                modifiedBy = modifiedBy,
                remark = remark,
                certificateData = certificateData,
                requestBytes = requestBytes
        )
    }

    private fun request() = PKCS10CertificationRequest(requestBytes)
}

@Entity
@Table(name = "certificate_data", indexes = arrayOf(Index(name = "IDX_PUB_KEY_HASH", columnList = "public_key_hash")))
class CertificateDataEntity(

        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        @Column(name = "public_key_hash", length = 64)
        val publicKeyHash: String,

        @Column(name = "certificate_status")
        val certificateStatus: CertificateStatus,

        @Lob
        @Column(name = "certificate_path_bytes")
        val certificatePathBytes: ByteArray,

        @OneToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "certificate_signing_request")
        val certificateSigningRequest: CertificateSigningRequestEntity
) {
    fun toCertificateData(): CertificateData {
        return CertificateData(
                publicKeyHash = publicKeyHash,
                certStatus = certificateStatus,
                certPath = toCertificatePath()
        )
    }

    private fun toCertificatePath(): CertPath = CertificateFactory.getInstance("X.509").generateCertPath(certificatePathBytes.inputStream())
}