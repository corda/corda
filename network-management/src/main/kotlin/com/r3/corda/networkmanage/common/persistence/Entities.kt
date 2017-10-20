package com.r3.corda.networkmanage.common.persistence

import org.hibernate.envers.Audited
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "certificate_signing_request", indexes = arrayOf(Index(name = "IDX_PUB_KEY_HASH", columnList = "public_key_hash")))
class CertificateSigningRequest(
        @Id
        @Column(name = "request_id", length = 64)
        var requestId: String = "",

        // TODO: Store X500Name with a proper schema.
        @Column(name = "legal_name", length = 256)
        var legalName: String = "",

        @Lob
        @Column
        var request: ByteArray = ByteArray(0),

        @Audited
        @Column(name = "status")
        @Enumerated(EnumType.STRING)
        var status: RequestStatus = RequestStatus.New,

        @Audited
        @Column(name = "modified_by", length = 512)
        @ElementCollection(targetClass = String::class, fetch = FetchType.EAGER)
        var modifiedBy: List<String> = emptyList(),

        @Audited
        @Column(name = "modified_at")
        var modifiedAt: Instant? = Instant.now(),

        @Audited
        @Column(name = "remark", length = 256, nullable = true)
        var remark: String? = null,

        // TODO: The certificate data can have its own table.
        @Embedded
        var certificateData: CertificateData? = null
)

@Embeddable
class CertificateData(
        @Column(name = "public_key_hash", length = 64, nullable = true)
        var publicKeyHash: String? = null,

        @Lob
        @Column(nullable = true)
        var certificatePath: ByteArray? = null,

        @Column(name = "certificate_status", nullable = true)
        var certificateStatus: CertificateStatus? = null
)

enum class CertificateStatus {
    VALID, SUSPENDED, REVOKED
}

enum class RequestStatus {
    New, Approved, Rejected, Signed
}

@Entity
@Table(name = "node_info")
class NodeInfoEntity(
        @Id
        @Column(name = "node_info_hash", length = 64)
        var nodeInfoHash: String = "",

        @Lob
        @Column(name = "node_info")
        var nodeInfo: ByteArray = ByteArray(0)
)

@Entity
@Table(name = "public_key_node_info_link")
class PublicKeyNodeInfoLink(
        @Id
        @Column(name = "public_key_hash", length = 64)
        var publicKeyHash: String = "",

        @Column(name = "node_info_hash", length = 64)
        var nodeInfoHash: String = ""
)