package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.persistence.RequestStatus
import net.corda.core.identity.CordaX500Name
import org.hibernate.envers.Audited
import java.io.Serializable
import java.math.BigInteger
import java.security.cert.CRLReason
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "cert_revocation_request")
data class CertificateRevocationRequestEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        @Column(name = "request_id", length = 64, nullable = false, unique = true)
        val requestId: String,

        @OneToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "cert_data", nullable = false)
        val certificateData: CertificateDataEntity,

        @Column(name = "cert_serial_number", nullable = false, columnDefinition = "NUMERIC(28)")
        val certificateSerialNumber: BigInteger,

        @Column(name = "legal_name", length = 256, nullable = false)
        @Convert(converter = CordaX500NameAttributeConverter::class)
        val legalName: CordaX500Name,

        // Setting [columnDefinition] is a work around for a hibernate problem when using SQL database.
        // TODO: Remove this when we find out the cause of the problem.
        @Audited
        @Column(name = "status", length = 16, nullable = false, columnDefinition = "NVARCHAR(16)")
        @Enumerated(EnumType.STRING)
        val status: RequestStatus = RequestStatus.NEW,

        @Column(name = "reporter", nullable = false, length = 64)
        val reporter: String,

        @Audited
        @Column(name = "modified_by", nullable = false, length = 64)
        val modifiedBy: String,

        @Audited
        @Column(name = "modified_at", nullable = false)
        val modifiedAt: Instant = Instant.now(),

        // Setting [columnDefinition] is a work around for a hibernate problem when using SQL database.
        // TODO: Remove this when we find out the cause of the problem.
        @Audited
        @Column(name = "revocation_reason", length = 32, nullable = false, columnDefinition = "NVARCHAR(32)")
        @Enumerated(EnumType.STRING)
        val revocationReason: CRLReason,

        @Audited
        @Column(name = "remark", length = 256)
        val remark: String? = null
) : Serializable