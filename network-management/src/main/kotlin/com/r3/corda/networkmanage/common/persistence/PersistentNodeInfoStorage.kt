/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.CertificateSigningRequestEntity
import com.r3.corda.networkmanage.common.persistence.entity.NodeInfoEntity
import com.r3.corda.networkmanage.common.utils.buildCertPath
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.CertRole
import net.corda.core.internal.CertRole.NODE_CA
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.security.cert.CertPath

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNodeInfoStorage(private val database: CordaPersistence) : NodeInfoStorage {
    override fun putNodeInfo(nodeInfoAndSigned: NodeInfoAndSigned): SecureHash {
        val (nodeInfo, signedNodeInfo) = nodeInfoAndSigned
        val nodeCaCert = nodeInfo.legalIdentitiesAndCerts[0].certPath.x509Certificates.find { CertRole.extract(it) == NODE_CA }
        nodeCaCert ?: throw IllegalArgumentException("Missing Node CA")
        return database.transaction {
            // TODO Move these checks out of data access layer
            val request = requireNotNull(getSignedRequestByPublicHash(nodeCaCert.publicKey.encoded.sha256())) {
                "Node-info not registered with us"
            }
            request.certificateData?.certificateStatus.let {
                require(it == CertificateStatus.VALID) { "Certificate is no longer valid: $it" }
            }

            // Update any [NodeInfoEntity] instance for this CSR as not current.
            entitiesWhere<NodeInfoEntity> { builder, path ->
                val requestEq = builder.equal(path.get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name), request)
                val isCurrent = builder.isTrue(path.get<Boolean>(NodeInfoEntity::isCurrent.name))
                builder.and(requestEq, isCurrent)
            }.resultStream.use { existingNodeInfos ->
                existingNodeInfos.forEach { session.merge(it.copy(isCurrent = false)) }
            }

            val hash = signedNodeInfo.raw.hash
            val hashedNodeInfo = NodeInfoEntity(
                    nodeInfoHash = hash.toString(),
                    certificateSigningRequest = request,
                    signedNodeInfoBytes = signedNodeInfo.serialize().bytes,
                    isCurrent = true)
            session.save(hashedNodeInfo)
            hash
        }
    }

    override fun getNodeInfo(nodeInfoHash: SecureHash): SignedNodeInfo? {
        return database.transaction {
            session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())?.toSignedNodeInfo()
        }
    }

    override fun getCertificatePath(publicKeyHash: SecureHash): CertPath? {
        return database.transaction {
            val request = getSignedRequestByPublicHash(publicKeyHash)
            request?.let { buildCertPath(it.certificateData!!.certificatePathBytes) }
        }
    }

    private fun DatabaseTransaction.getSignedRequestByPublicHash(publicKeyHash: SecureHash): CertificateSigningRequestEntity? {
        return uniqueEntityWhere { builder, path ->
            val publicKeyEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::publicKeyHash.name), publicKeyHash.toString())
            val statusEq = builder.equal(path.get<RequestStatus>(CertificateSigningRequestEntity::status.name), RequestStatus.DONE)
            builder.and(publicKeyEq, statusEq)
        }
    }
}