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
import com.r3.corda.networkmanage.common.utils.logger
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.CertRole
import net.corda.core.internal.CertRole.NODE_CA
import net.corda.core.internal.hash
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.security.PublicKey
import java.security.cert.CertPath
import java.time.Instant

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNodeInfoStorage(private val database: CordaPersistence) : NodeInfoStorage {
    override fun putNodeInfo(nodeInfoAndSigned: NodeInfoAndSigned): SecureHash {
        val (nodeInfo, signedNodeInfo) = nodeInfoAndSigned
        val nodeInfoHash = signedNodeInfo.raw.hash

        // Extract identities issued by the intermediate CAs (doorman).
        val registeredIdentities = nodeInfo.legalIdentitiesAndCerts.map { it.certPath.x509Certificates.single { CertRole.extract(it) in setOf(CertRole.SERVICE_IDENTITY, NODE_CA) } }

        database.transaction {
            // Record fact of republishing of the node info, it's treated as a heartbeat from the node.
            val rowsUpdated = session.createQuery("update ${NodeInfoEntity::class.java.name} n set publishedAt = :now " +
                    "where n.nodeInfoHash = :nodeInfoHash and n.isCurrent = true")
                    .setParameter("now", Instant.now())
                    .setParameter("nodeInfoHash", nodeInfoHash.toString())
                    .executeUpdate()
            if (rowsUpdated != 0) {
                logger.debug { "Republish of $nodeInfo" }
                return@transaction nodeInfoHash
            }
            // TODO Move these checks out of data access layer
            // For each identity known by the doorman, validate against it's CSR.
            val requests = registeredIdentities.map {
                val request = requireNotNull(getSignedRequestByPublicHash(it.publicKey.hash)) {
                    "Node-info not registered with us"
                }
                request.certificateData?.certificateStatus.let {
                    require(it == CertificateStatus.VALID) { "Certificate is no longer valid: $it" }
                }
                CertRole.extract(it) to request
            }

            // Ensure only 1 NodeCA identity.
            // TODO: make this support multiple node identities.
            val (_, request) = requireNotNull(requests.singleOrNull { it.first == CertRole.NODE_CA }) { "Require exactly 1 Node CA identity in the node-info." }

            val existingNodeInfos = session.fromQuery<NodeInfoEntity>(
                    "n where n.certificateSigningRequest = :csr and n.isCurrent = true order by n.publishedAt desc")
                    .setParameter("csr", request)
                    .resultList

            // Update any [NodeInfoEntity] instance for this CSR as not current.
            existingNodeInfos.forEach { session.merge(it.copy(isCurrent = false)) }

            session.saveOrUpdate(NodeInfoEntity(
                    nodeInfoHash = nodeInfoHash.toString(),
                    publicKeyHash = nodeInfo.legalIdentities[0].owningKey.hash,
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

    override fun ackNodeInfoParametersUpdate(publicKey: PublicKey, acceptedParametersHash: SecureHash) {
        return database.transaction {
            val nodeInfoEntity = session.fromQuery<NodeInfoEntity>(
                    "n where n.publicKeyHash = :publicKeyHash and isCurrent = true")
                    .setParameter("publicKeyHash", publicKey.hash)
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
            val publicKeyEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::publicKeyHash.name), publicKeyHash)
            val statusEq = builder.equal(path.get<RequestStatus>(CertificateSigningRequestEntity::status.name), RequestStatus.DONE)
            builder.and(publicKeyEq, statusEq)
        }
    }
}