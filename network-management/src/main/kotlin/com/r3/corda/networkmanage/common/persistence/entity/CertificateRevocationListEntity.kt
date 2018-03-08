package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import org.hibernate.envers.Audited
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "certificate_revocation_list")
class CertificateRevocationListEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val id: Long? = null,

        @Column(name = "issuer")
        val crlIssuer: CrlIssuer,

        @Lob
        @Column(name = "crl_bytes", nullable = false)
        val crlBytes: ByteArray,

        @Audited
        @Column(name = "signed_by", length = 512)
        val signedBy: String,

        @Audited
        @Column(name = "modified_at", nullable = false)
        val modifiedAt: Instant = Instant.now()
)