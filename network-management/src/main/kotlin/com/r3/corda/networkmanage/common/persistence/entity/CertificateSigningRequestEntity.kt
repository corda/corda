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
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.x509Certificates
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.hibernate.envers.Audited
import java.math.BigInteger
import java.security.cert.CertPath
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "certificate_signing_request", indexes = arrayOf(Index(name = "IDX_PUB_KEY_HASH", columnList = "public_key_hash")))
data class CertificateSigningRequestEntity(
        @Id
        @Column(name = "request_id", length = 64)
        val requestId: String,

        // TODO: Store X500Name with a proper schema.
        @Column(name = "legal_name", length = 256)
        @Convert(converter = CordaX500NameAttributeConverter::class)
        val legalName: CordaX500Name?,

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
        val requestBytes: ByteArray,

        @ManyToOne
        @JoinColumn(name = "private_network", foreignKey = ForeignKey(name = "FK_CSR_PN"))
        val privateNetwork: PrivateNetworkEntity? = null
) {
    fun toCertificateSigningRequest(): CertificateSigningRequest {
        return CertificateSigningRequest(
                requestId = requestId,
                legalName = legalName,
                publicKeyHash = SecureHash.parse(publicKeyHash),
                status = status,
                request = request(),
                remark = remark,
                modifiedBy = modifiedBy,
                certData = certificateData?.toCertificateData()
        )
    }

    private fun request() = PKCS10CertificationRequest(requestBytes)
}

@Entity
@Table(name = "certificate_data")
data class CertificateDataEntity(
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

    val legalName: CordaX500Name get() {
        return CordaX500Name.build(toCertificatePath().x509Certificates[0].subjectX500Principal)
    }

    fun copy(certificateStatus: CertificateStatus = this.certificateStatus,
             certificatePathBytes: ByteArray = this.certificatePathBytes,
             certificateSigningRequest: CertificateSigningRequestEntity = this.certificateSigningRequest,
             certificateSerialNumber: BigInteger = this.certificateSerialNumber): CertificateDataEntity {
        return CertificateDataEntity(
                id = this.id,
                certificateStatus = certificateStatus,
                certificatePathBytes = certificatePathBytes,
                certificateSigningRequest = certificateSigningRequest,
                certificateSerialNumber = certificateSerialNumber)
    }

    private fun toCertificatePath(): CertPath = buildCertPath(certificatePathBytes)
}

@Entity
@Table(name = "private_network")
data class PrivateNetworkEntity(
        @Id
        @Column(name = "id")
        val networkId: String,

        @Column(name = "name")
        val networkName: String
)