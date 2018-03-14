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

import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.SignedNodeInfo
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "node_info")
class NodeInfoEntity(
        // Hash of serialized [NodeInfo] without signatures.
        @Id
        @Column(name = "node_info_hash", length = 64)
        val nodeInfoHash: String = "",

        @ManyToOne(optional = false, fetch = FetchType.LAZY)
        @JoinColumn(name = "certificate_signing_request")
        val certificateSigningRequest: CertificateSigningRequestEntity,

        @Lob
        @Column(name = "signed_node_info_bytes")
        val signedNodeInfoBytes: ByteArray,

        @Column(name="is_current")
        val isCurrent: Boolean,

        @Column(name = "published_at")
        val publishedAt: Instant = Instant.now()
) {
    /**
     * Deserializes NodeInfoEntity.soignedNodeInfoBytes into the [SignedNodeInfo] instance
     */
    fun signedNodeInfo() = signedNodeInfoBytes.deserialize<SignedNodeInfo>()

    fun copy(nodeInfoHash: String = this.nodeInfoHash,
             certificateSigningRequest: CertificateSigningRequestEntity = this.certificateSigningRequest,
             signedNodeInfoBytes: ByteArray = this.signedNodeInfoBytes,
             isCurrent: Boolean = this.isCurrent,
             publishedAt: Instant = this.publishedAt
    ): NodeInfoEntity {
        return NodeInfoEntity(
                nodeInfoHash = nodeInfoHash,
                certificateSigningRequest = certificateSigningRequest,
                signedNodeInfoBytes = signedNodeInfoBytes,
                isCurrent = isCurrent,
                publishedAt = publishedAt
        )
    }
}
