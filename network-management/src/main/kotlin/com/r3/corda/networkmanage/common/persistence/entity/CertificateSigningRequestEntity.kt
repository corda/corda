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
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.x509Certificates
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.hibernate.envers.Audited
import java.math.BigInteger
import java.security.cert.CertPath
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "cert_signing_request", indexes = arrayOf(Index(name = "IDX__CSR__PKH", columnList = "public_key_hash")))
data class CertificateSigningRequestEntity(
        @Id
        @Column(name = "request_id", length = 64, nullable = false)
        val requestId: String,

        // TODO: Store X500Name with a proper schema.
        @Column(name = "legal_name", length = 256)
        @Convert(converter = CordaX500NameAttributeConverter::class)
        val legalName: CordaX500Name?,

        @Column(name = "public_key_hash", length = 64, nullable = false)
        val publicKeyHash: String,

        // Setting [columnDefinition] is a work around for a hibernate problem when using SQL database.
        // TODO: Remove this when we find out the cause of the problem.
        @Audited
        @Column(name = "status", length = 16, nullable = false, columnDefinition = "NVARCHAR(16)")
        @Enumerated(EnumType.STRING)
        val status: RequestStatus = RequestStatus.NEW,

        @Audited
        @Column(name = "modified_by", length = 64, nullable = false)
        val modifiedBy: String,

        // TODO: Use audit framework instead.
        @Column(name = "modified_at", nullable = false)
        val modifiedAt: Instant = Instant.now(),

        @Audited
        @Column(name = "remark", length = 256)
        val remark: String? = null,

        @OneToOne(fetch = FetchType.EAGER, mappedBy = "certificateSigningRequest")
        val certificateData: CertificateDataEntity? = null,

        @Lob
        @Column(name = "request_bytes", nullable = false)
        @Convert(converter = PKCS10CertificationRequestConverter::class)
        val request: PKCS10CertificationRequest,

        @ManyToOne
        @JoinColumn(name = "private_network", foreignKey = ForeignKey(name = "FK__CSR__PN"))
        val privateNetwork: PrivateNetworkEntity? = null
) {
    fun toCertificateSigningRequest(): CertificateSigningRequest {
        return CertificateSigningRequest(
                requestId = requestId,
                legalName = legalName,
                publicKeyHash = SecureHash.parse(publicKeyHash),
                status = status,
                request = request,
                remark = remark,
                modifiedBy = modifiedBy,
                certData = certificateData?.toCertificateData()
        )
    }
}

@Entity
@Table(name = "cert_data")
data class CertificateDataEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        // Setting [columnDefinition] is a work around for a hibernate problem when using SQL database.
        // TODO: Remove this when we find out the cause of the problem.
        @Column(name = "cert_status", length = 16, nullable = false, columnDefinition = "NVARCHAR(16)")
        @Enumerated(EnumType.STRING)
        val certificateStatus: CertificateStatus,

        @Lob
        @Column(name = "cert_path_bytes", nullable = false)
        @Convert(converter = CertPathConverter::class)
        val certPath: CertPath,

        @OneToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "cert_signing_request", foreignKey = ForeignKey(name = "FK__CD__CSR"), nullable = false)
        val certificateSigningRequest: CertificateSigningRequestEntity,

        @Column(name = "cert_serial_number", unique = true, nullable = false, columnDefinition = "NUMERIC(28)")
        val certificateSerialNumber: BigInteger
) {
    fun toCertificateData(): CertificateData = CertificateData(certificateStatus, certPath)

    val legalName: CordaX500Name get() {
        return CordaX500Name.build(certPath.x509Certificates[0].subjectX500Principal)
    }
}

@Entity
@Table(name = "private_network")
data class PrivateNetworkEntity(
        @Id
        @Column(name = "id", length = 64)
        val networkId: String,

        @Column(name = "name", length = 255, nullable = false)
        val networkName: String
)