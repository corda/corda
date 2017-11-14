package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.CertificateDataEntity
import com.r3.corda.networkmanage.common.persistence.entity.CertificateSigningRequestEntity
import com.r3.corda.networkmanage.common.persistence.entity.NodeInfoEntity
import com.r3.corda.networkmanage.common.utils.buildCertPath
import com.r3.corda.networkmanage.common.utils.hashString
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sha256
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.node.utilities.CordaPersistence
import org.hibernate.Session
import org.hibernate.jpa.QueryHints
import java.security.cert.CertPath
import java.sql.Connection

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNodeInfoStorage(private val database: CordaPersistence) : NodeInfoStorage {
    override fun putNodeInfo(nodeInfo: NodeInfo, signature: DigitalSignature?): SecureHash = database.transaction(Connection.TRANSACTION_SERIALIZABLE) {
        val publicKeyHash = nodeInfo.legalIdentities.first().owningKey.hashString()
        val request = singleRequestWhere(CertificateDataEntity::class.java) { builder, path ->
            val certPublicKeyHashEq = builder.equal(path.get<String>(CertificateDataEntity::publicKeyHash.name), publicKeyHash)
            val certStatusValid = builder.equal(path.get<CertificateStatus>(CertificateDataEntity::certificateStatus.name), CertificateStatus.VALID)
            builder.and(certPublicKeyHashEq, certStatusValid)
        }
        request ?: throw IllegalArgumentException("CSR data missing for provided node info: $nodeInfo")
        /*
         * Delete any previous [HashedNodeInfo] instance for this CSR
         * Possibly it should be moved at the network signing process at the network signing process
         * as for a while the network map will have invalid entries (i.e. hashes for node info which have been
         * removed). Either way, there will be a period of time when the network map data will be invalid
         * but it has been confirmed that this fact has been acknowledged at the design time and we are fine with it.
         */
        deleteRequest(NodeInfoEntity::class.java) { builder, path ->
            builder.equal(path.get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name), request.certificateSigningRequest)
        }
        val serializedNodeInfo = nodeInfo.serialize().bytes
        val hash = serializedNodeInfo.sha256()
        val hashedNodeInfo = NodeInfoEntity(
                nodeInfoHash = hash.toString(),
                certificateSigningRequest = request.certificateSigningRequest,
                nodeInfoBytes = serializedNodeInfo,
                signatureBytes = signature?.bytes)
        session.save(hashedNodeInfo)
        hash
    }

    override fun getSignedNodeInfo(nodeInfoHash: SecureHash): SignedData<NodeInfo>? = database.transaction {
        val nodeInfoEntity = session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())
        if (nodeInfoEntity?.signatureBytes == null) {
            null
        } else {
            SignedData(SerializedBytes(nodeInfoEntity.nodeInfoBytes), nodeInfoEntity.signature()!!)
        }
    }

    override fun getNodeInfo(nodeInfoHash: SecureHash): NodeInfo? = database.transaction {
        session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())?.nodeInfo()
    }

    override fun getUnsignedNodeInfoBytes(): Map<SecureHash, ByteArray> {
        return getUnsignedNodeInfoEntities().associate { SecureHash.parse(it.nodeInfoHash) to it.nodeInfoBytes }
    }

    override fun getUnsignedNodeInfoHashes(): List<SecureHash> {
        return getUnsignedNodeInfoEntities().map { SecureHash.parse(it.nodeInfoHash) }
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

    override fun signNodeInfo(nodeInfoHash: SecureHash, signature: DigitalSignature.WithKey) {
        database.transaction {
            val nodeInfoEntity = session.find(NodeInfoEntity::class.java, nodeInfoHash.toString())
            if (nodeInfoEntity != null) {
                session.merge(nodeInfoEntity.copy(
                        signatureBytes = signature.bytes,
                        signaturePublicKeyAlgorithm = signature.by.algorithm,
                        signaturePublicKeyBytes = signature.by.encoded
                ))
            }
        }
    }

    private fun getUnsignedNodeInfoEntities(): List<NodeInfoEntity> = database.transaction {
        val builder = session.criteriaBuilder
        // Retrieve all unsigned NodeInfoHash
        val query = builder.createQuery(NodeInfoEntity::class.java).run {
            from(NodeInfoEntity::class.java).run {
                where(builder.and(builder.isNull(get<ByteArray>(NodeInfoEntity::signatureBytes.name))))
            }
        }
        // Retrieve them together with their CSR
        val (hintKey, hintValue) = getNodeInfoWithCsrHint(session)
        val unsigned = session.createQuery(query).setHint(hintKey, hintValue).resultList
        // Get only those that are valid
        unsigned.filter({
            val certificateStatus = it.certificateSigningRequest?.certificateData?.certificateStatus
            certificateStatus == CertificateStatus.VALID
        })
    }

    /**
     * Creates Hibernate query hint for pulling [CertificateSigningRequestEntity] when querying for [NodeInfoEntity]
     */
    private fun getNodeInfoWithCsrHint(session: Session): Pair<String, Any> {
        val graph = session.createEntityGraph(NodeInfoEntity::class.java)
        graph.addAttributeNodes(NodeInfoEntity::certificateSigningRequest.name)
        return QueryHints.HINT_LOADGRAPH to graph
    }
}