package com.r3.corda.networkmanage.common.persistence.entity

import net.corda.core.crypto.DigitalSignature
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.persistence.*

@Entity
@Table(name = "node_info")
class NodeInfoEntity(
        @Id
        @Column(name = "node_info_hash", length = 64)
        val nodeInfoHash: String = "",

        @OneToOne(optional = false, fetch = FetchType.LAZY)
        @JoinColumn(name = "certificate_signing_request", nullable = true)
        val certificateSigningRequest: CertificateSigningRequestEntity? = null,

        @Lob
        @Column(name = "node_info_bytes")
        val nodeInfoBytes: ByteArray,

        @Lob
        @Column(name = "signature_bytes", nullable = true)
        val signatureBytes: ByteArray? = null,

        @Lob
        @Column(name = "signature_public_key_bytes", nullable = true)
        val signaturePublicKeyBytes: ByteArray? = null,

        @Lob
        @Column(name = "signature_public_key_algorithm", nullable = true)
        val signaturePublicKeyAlgorithm: String? = null
) {
    /**
     * Deserializes NodeInfoEntity.nodeInfoBytes into the [NodeInfo] instance
     */
    fun nodeInfo() = nodeInfoBytes.deserialize<NodeInfo>()

    /**
     * Deserializes NodeInfoEntity.signatureBytes into the [DigitalSignature.WithKey] instance together with its public key
     */
    fun signature(): DigitalSignature.WithKey? {
        return if (signatureBytes != null && signaturePublicKeyBytes != null && !signaturePublicKeyAlgorithm.isNullOrEmpty()) {
            DigitalSignature.WithKey(KeyFactory.getInstance(signaturePublicKeyAlgorithm).generatePublic(X509EncodedKeySpec(signaturePublicKeyBytes)), signatureBytes)
        } else {
            null
        }
    }

    fun copy(nodeInfoHash: String = this.nodeInfoHash,
             certificateSigningRequest: CertificateSigningRequestEntity? = this.certificateSigningRequest,
             nodeInfoBytes: ByteArray = this.nodeInfoBytes,
             signatureBytes: ByteArray? = this.signatureBytes,
             signaturePublicKeyBytes: ByteArray? = this.signaturePublicKeyBytes,
             signaturePublicKeyAlgorithm: String? = this.signaturePublicKeyAlgorithm
    ): NodeInfoEntity {
        return NodeInfoEntity(
                nodeInfoHash = nodeInfoHash,
                certificateSigningRequest = certificateSigningRequest,
                nodeInfoBytes = nodeInfoBytes,
                signatureBytes = signatureBytes,
                signaturePublicKeyBytes = signaturePublicKeyBytes,
                signaturePublicKeyAlgorithm = signaturePublicKeyAlgorithm
        )
    }
}


