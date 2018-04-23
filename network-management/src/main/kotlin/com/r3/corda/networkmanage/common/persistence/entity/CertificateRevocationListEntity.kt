package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import java.io.Serializable
import java.security.cert.X509CRL
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "cert_revocation_list")
class CertificateRevocationListEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        @Column(name = "issuer", length = 16, nullable = false, columnDefinition = "NVARCHAR(16)")
        @Enumerated(EnumType.STRING)
        val crlIssuer: CrlIssuer,

        @Lob
        @Column(name = "crl_bytes", nullable = false)
        @Convert(converter = X509CRLConverter::class)
        val crl: X509CRL,

        @Column(name = "signed_by", length = 64, nullable = false)
        val signedBy: String,

        @Column(name = "modified_at", nullable = false)
        val modifiedAt: Instant = Instant.now()
) : Serializable