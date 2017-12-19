package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.CertificateDataEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateSigningRequestEntity
import com.r3.corda.networkmanage.common.persistence.entity.NodeInfoEntity
import com.r3.corda.networkmanage.common.utils.buildCertPath
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.CertRole
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import java.security.cert.CertPath

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNodeInfoStorage(private val database: CordaPersistence) : NodeInfoStorage {
    override fun putNodeInfo(signedNodeInfo: SignedNodeInfo): SecureHash {
        return database.transaction(TransactionIsolationLevel.SERIALIZABLE) {
            val nodeInfo = signedNodeInfo.verified()
            val nodeCaCert = nodeInfo.legalIdentitiesAndCerts[0].certPath.certificates.find { CertRole.extract(it) == CertRole.NODE_CA }

            val request = nodeCaCert?.let {
                singleRequestWhere(CertificateDataEntity::class.java) { builder, path ->
                    val certPublicKeyHashEq = builder.equal(path.get<String>(CertificateDataEntity::publicKeyHash.name), it.publicKey.encoded.sha256().toString())
                    val certStatusValid = builder.equal(path.get<CertificateStatus>(CertificateDataEntity::certificateStatus.name), CertificateStatus.VALID)
                    builder.and(certPublicKeyHashEq, certStatusValid)
                }
            }
            request ?: throw IllegalArgumentException("Unknown node info, this public key is not registered with the network management service.")

            /*
             * Delete any previous [NodeInfoEntity] instance for this CSR
             * Possibly it should be moved at the network signing process at the network signing process
             * as for a while the network map will have invalid entries (i.e. hashes for node info which have been
             * removed). Either way, there will be a period of time when the network map data will be invalid
             * but it has been confirmed that this fact has been acknowledged at the design time and we are fine with it.
             */
            deleteRequest(NodeInfoEntity::class.java) { builder, path ->
                builder.equal(path.get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name), request.certificateSigningRequest)
            }
            val hash = signedNodeInfo.raw.hash

            val hashedNodeInfo = NodeInfoEntity(
                    nodeInfoHash = hash.toString(),
                    certificateSigningRequest = request.certificateSigningRequest,
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
            val builder = session.criteriaBuilder
            val query = builder.createQuery(ByteArray::class.java).run {
                from(CertificateSigningRequestEntity::class.java).run {
                    select(get<CertificateDataEntity>(CertificateSigningRequestEntity::certificateData.name)
                            .get<ByteArray>(CertificateDataEntity::certificatePathBytes.name))
                    where(builder.equal(get<CertificateDataEntity>(CertificateSigningRequestEntity::certificateData.name)
                            .get<String>(CertificateDataEntity::publicKeyHash.name), publicKeyHash.toString()))
                }
            }
            session.createQuery(query).uniqueResultOptional().orElseGet { null }?.let { buildCertPath(it) }
        }
    }
}