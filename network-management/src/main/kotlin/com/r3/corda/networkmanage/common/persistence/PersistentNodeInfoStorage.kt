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
import com.r3.corda.networkmanage.common.persistence.entity.NetworkParametersEntity
import com.r3.corda.networkmanage.common.persistence.entity.NodeInfoEntity
import com.r3.corda.networkmanage.common.utils.hashString
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.CertRole
import net.corda.core.internal.CertRole.NODE_CA
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
        database.transaction {
            // TODO Move these checks out of data access layer
            val request = requireNotNull(getSignedRequestByPublicHash(nodeCaCert.publicKey.encoded.sha256())) {
                "Node-info not registered with us"
            }
            request.certificateData?.certificateStatus.let {
                require(it == CertificateStatus.VALID) { "Certificate is no longer valid: $it" }
            }

            val existingNodeInfos = session.createQuery(
                    "from ${NodeInfoEntity::class.java.name} n where n.certificateSigningRequest = :csr and n.isCurrent = true order by n.publishedAt desc",
                    NodeInfoEntity::class.java)
                    .setParameter("csr", request)
                    .resultList

            // Update any [NodeInfoEntity] instance for this CSR as not current.
            existingNodeInfos.forEach { session.merge(it.copy(isCurrent = false)) }

            session.save(NodeInfoEntity(
                    nodeInfoHash = signedNodeInfo.raw.hash.toString(),
                    publicKeyHash = nodeInfo.legalIdentities[0].owningKey.hashString(),
                    certificateSigningRequest = request,
                    signedNodeInfo = signedNodeInfo,
                    isCurrent = true,
                    acceptedNetworkParameters = existingNodeInfos.firstOrNull()?.acceptedNetworkParameters
            ))
        }
        return signedNodeInfo.raw.hash
    }

    override fun getNodeInfo(nodeInfoHash: SecureHash): SignedNodeInfo? {
        return database.transaction {
            session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())?.signedNodeInfo
        }
    }

    override fun getAcceptedNetworkParameters(nodeInfoHash: SecureHash): NetworkParametersEntity? {
        return database.transaction {
            session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())?.acceptedNetworkParameters
        }
    }

    override fun getCertificatePath(publicKeyHash: SecureHash): CertPath? {
        return database.transaction {
            val request = getSignedRequestByPublicHash(publicKeyHash)
            request?.let { it.certificateData!!.certPath }
        }
    }

    override fun ackNodeInfoParametersUpdate(publicKeyHash: SecureHash, acceptedParametersHash: SecureHash) {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(NodeInfoEntity::class.java).run {
                from(NodeInfoEntity::class.java).run {
                    where(builder.equal(get<NodeInfoEntity>(NodeInfoEntity::publicKeyHash.name), publicKeyHash.toString()))
                }
            }
            val nodeInfo = requireNotNull(session.createQuery(query).setMaxResults(1).uniqueResult()) {
                "NodeInfo with public key hash $publicKeyHash doesn't exist"
            }
            val networkParameters = requireNotNull(getNetworkParametersEntity(acceptedParametersHash)) {
                "Network parameters $acceptedParametersHash doesn't exist"
            }
            require(networkParameters.isSigned) { "Network parameters $acceptedParametersHash is not signed" }
            val newInfo = nodeInfo.copy(acceptedNetworkParameters = networkParameters)
            session.merge(newInfo)
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