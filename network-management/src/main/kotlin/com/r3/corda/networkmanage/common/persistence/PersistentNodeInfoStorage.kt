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
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.security.cert.CertPath

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNodeInfoStorage(private val database: CordaPersistence) : NodeInfoStorage {
    override fun putNodeInfo(nodeInfoWithSigned: NodeInfoWithSigned): SecureHash {
        val nodeInfo = nodeInfoWithSigned.nodeInfo
        val signedNodeInfo = nodeInfoWithSigned.signedNodeInfo
        val nodeCaCert = nodeInfo.legalIdentitiesAndCerts[0].certPath.x509Certificates.find { CertRole.extract(it) == CertRole.NODE_CA }
        return database.transaction {
            // TODO Move these checks out of data access layer
            val request = nodeCaCert?.let {
                getSignedRequestByPublicHash(it.publicKey.encoded.sha256(), this)
            }
            request ?: throw IllegalArgumentException("Unknown node info, this public key is not registered with the network management service.")
            require(request.certificateData!!.certificateStatus == CertificateStatus.VALID) { "Certificate is no longer valid" }

            /*
             * Delete any previous [NodeInfoEntity] instance for this CSR
             * Possibly it should be moved at the network signing process at the network signing process
             * as for a while the network map will have invalid entries (i.e. hashes for node info which have been
             * removed). Either way, there will be a period of time when the network map data will be invalid
             * but it has been confirmed that this fact has been acknowledged at the design time and we are fine with it.
             */
            deleteRequest(NodeInfoEntity::class.java) { builder, path ->
                builder.equal(path.get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name), request)
            }
            val hash = signedNodeInfo.raw.hash

            val hashedNodeInfo = NodeInfoEntity(
                    nodeInfoHash = hash.toString(),
                    certificateSigningRequest = request,
                    signedNodeInfoBytes = signedNodeInfo.serialize().bytes)
            session.save(hashedNodeInfo)
            hash
        }
    }

    override fun getNodeInfo(nodeInfoHash: SecureHash): SignedNodeInfo? {
        return database.transaction {
            session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())?.signedNodeInfo()
        }
    }

    override fun getCertificatePath(publicKeyHash: SecureHash): CertPath? {
        return database.transaction {
            val request = getSignedRequestByPublicHash(publicKeyHash, this)
            request?.let { buildCertPath(it.certificateData!!.certificatePathBytes) }
        }
    }

    private fun getSignedRequestByPublicHash(publicKeyHash: SecureHash, transaction: DatabaseTransaction): CertificateSigningRequestEntity? {
        return transaction.singleRequestWhere(CertificateSigningRequestEntity::class.java) { builder, path ->
            val publicKeyEq = builder.equal(path.get<String>(CertificateSigningRequestEntity::publicKeyHash.name), publicKeyHash.toString())
            val statusEq = builder.equal(path.get<RequestStatus>(CertificateSigningRequestEntity::status.name), RequestStatus.DONE)
            builder.and(publicKeyEq, statusEq)
        }
    }
}