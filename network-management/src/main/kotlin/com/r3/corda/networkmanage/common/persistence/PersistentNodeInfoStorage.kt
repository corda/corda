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
import com.r3.corda.networkmanage.common.persistence.entity.ParametersUpdateEntity
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus
import com.r3.corda.networkmanage.common.utils.hashString
import com.r3.corda.networkmanage.common.utils.logger
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
        val nodeInfoHash = signedNodeInfo.raw.hash

        database.transaction {
            val count = session.createQuery(
                    "select count(*) from ${NodeInfoEntity::class.java.name} where nodeInfoHash = :nodeInfoHash", java.lang.Long::class.java)
                    .setParameter("nodeInfoHash", nodeInfoHash.toString())
                    .singleResult
                    .toLong()
            if (count != 0L) {
                logger.debug("Ignoring duplicate publish: $nodeInfo")
                return@transaction nodeInfoHash
            }

            // TODO Move these checks out of data access layer
            val request = requireNotNull(getSignedRequestByPublicHash(nodeCaCert.publicKey.encoded.sha256())) {
                "Node-info not registered with us"
            }
            request.certificateData?.certificateStatus.let {
                require(it == CertificateStatus.VALID) { "Certificate is no longer valid: $it" }
            }

            val existingNodeInfos = session.fromQuery<NodeInfoEntity>(
                    "n where n.certificateSigningRequest = :csr and n.isCurrent = true order by n.publishedAt desc")
                    .setParameter("csr", request)
                    .resultList

            // Update any [NodeInfoEntity] instance for this CSR as not current.
            existingNodeInfos.forEach { session.merge(it.copy(isCurrent = false)) }

            session.save(NodeInfoEntity(
                    nodeInfoHash = nodeInfoHash.toString(),
                    publicKeyHash = nodeInfo.legalIdentities[0].owningKey.hashString(),
                    certificateSigningRequest = request,
                    signedNodeInfo = signedNodeInfo,
                    isCurrent = true,
                    acceptedParametersUpdate = existingNodeInfos.firstOrNull()?.acceptedParametersUpdate
            ))
        }

        return nodeInfoHash
    }

    override fun getNodeInfo(nodeInfoHash: SecureHash): SignedNodeInfo? {
        return database.transaction {
            session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())?.signedNodeInfo
        }
    }

    override fun getAcceptedParametersUpdate(nodeInfoHash: SecureHash): ParametersUpdateEntity? {
        return database.transaction {
            session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())?.acceptedParametersUpdate
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
            val nodeInfoEntity = session.fromQuery<NodeInfoEntity>(
                    "n where n.publicKeyHash = :publicKeyHash and isCurrent = true")
                    .setParameter("publicKeyHash", publicKeyHash.toString())
                    .singleResult
            val parametersUpdateEntity = session.fromQuery<ParametersUpdateEntity>(
                    "u where u.networkParameters.hash = :acceptedParametersHash").
                    setParameter("acceptedParametersHash", acceptedParametersHash.toString())
                    .singleResult
            require(parametersUpdateEntity.status in listOf(UpdateStatus.NEW, UpdateStatus.FLAG_DAY)) {
                "$parametersUpdateEntity can no longer be accepted as it's ${parametersUpdateEntity.status}"
            }
            session.merge(nodeInfoEntity.copy(acceptedParametersUpdate = parametersUpdateEntity))
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