/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.persistence.CertificateData
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.common.utils.buildCertPath
import net.corda.core.crypto.SecureHash
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.hibernate.envers.Audited
import java.math.BigInteger
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "certificate_signing_request", indexes = arrayOf(Index(name = "IDX_PUB_KEY_HASH", columnList = "public_key_hash")))
class CertificateSigningRequestEntity(
        @Id
        @Column(name = "request_id", length = 64)
        val requestId: String,

        // TODO: Store X500Name with a proper schema.
        @Column(name = "legal_name", length = 256, nullable = false)
        val legalName: String,

        @Column(name = "public_key_hash", length = 64)
        val publicKeyHash: String,

        @Audited
        @Column(name = "status", nullable = false)
        @Enumerated(EnumType.STRING)
        val status: RequestStatus = RequestStatus.NEW,

        @Audited
        @Column(name = "modified_by", length = 512)
        val modifiedBy: String,

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
            publicKeyHash = SecureHash.parse(publicKeyHash),
            status = status,
            request = request(),
            remark = remark,
            modifiedBy = modifiedBy,
            certData = certificateData?.toCertificateData()
    )

    fun copy(requestId: String = this.requestId,
             legalName: String = this.legalName,
             publicKeyHash: String = this.publicKeyHash,
             status: RequestStatus = this.status,
             modifiedBy: String = this.modifiedBy,
             modifiedAt: Instant = this.modifiedAt,
             remark: String? = this.remark,
             certificateData: CertificateDataEntity? = this.certificateData,
             requestBytes: ByteArray = this.requestBytes
    ): CertificateSigningRequestEntity {
        return CertificateSigningRequestEntity(
                requestId = requestId,
                legalName = legalName,
                publicKeyHash = publicKeyHash,
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
@Table(name = "certificate_data")
class CertificateDataEntity(

        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        @Column(name = "certificate_status")
        val certificateStatus: CertificateStatus,

        @Lob
        @Column(name = "certificate_path_bytes")
        val certificatePathBytes: ByteArray,

        @OneToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "certificate_signing_request", foreignKey = ForeignKey(name = "FK__cert_data__cert_sign_req"))
        val certificateSigningRequest: CertificateSigningRequestEntity,

        @Column(name = "certificate_serial_number", unique = true)
        val certificateSerialNumber: BigInteger
) {
    fun toCertificateData(): CertificateData {
        return CertificateData(
                certStatus = certificateStatus,
                certPath = toCertificatePath()
        )
    }

    fun legalName(): String {
        return (toCertificatePath().certificates.first() as X509Certificate).subjectX500Principal.name
    }

    private fun toCertificatePath(): CertPath = buildCertPath(certificatePathBytes)
}