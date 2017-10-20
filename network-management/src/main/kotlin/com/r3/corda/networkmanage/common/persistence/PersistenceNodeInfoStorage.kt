package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.utils.buildCertPath
import net.corda.core.crypto.sha256
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.PersistentMap
import java.security.cert.CertPath

class PersistenceNodeInfoStorage(private val database: CordaPersistence) : NodeInfoStorage {
    companion object {
        fun makeNodeInfoMap() = PersistentMap<String, NodeInfo, NodeInfoEntity, String>(
                toPersistentEntityKey = { it },
                toPersistentEntity = { key, nodeInfo ->
                    val serializedNodeInfo = nodeInfo.serialize()
                    NodeInfoEntity(key, serializedNodeInfo.bytes)
                },
                fromPersistentEntity = {
                    val nodeInfo = it.nodeInfo.deserialize<NodeInfo>()
                    it.nodeInfoHash to nodeInfo
                },
                persistentEntityClass = NodeInfoEntity::class.java
        )

        fun makePublicKeyMap() = PersistentMap<String, String, PublicKeyNodeInfoLink, String>(
                toPersistentEntityKey = { it },
                toPersistentEntity = { publicKeyHash, nodeInfoHash -> PublicKeyNodeInfoLink(publicKeyHash, nodeInfoHash) },
                fromPersistentEntity = { it.publicKeyHash to it.nodeInfoHash },
                persistentEntityClass = PublicKeyNodeInfoLink::class.java
        )
    }

    private val nodeInfoMap = database.transaction { makeNodeInfoMap() }
    private val publicKeyMap = database.transaction { makePublicKeyMap() }

    override fun putNodeInfo(nodeInfo: NodeInfo) {
        return database.transaction {
            val publicKeyHash = nodeInfo.legalIdentities.first().owningKey.hash()
            val nodeInfoHash = nodeInfo.serialize().sha256().toString()
            val existingNodeInfoHash = publicKeyMap[publicKeyHash]
            if (nodeInfoHash != existingNodeInfoHash) {
                // Remove node info if exists.
                existingNodeInfoHash?.let { nodeInfoMap.remove(it) }
                publicKeyMap[publicKeyHash] = nodeInfoHash
                nodeInfoMap.put(nodeInfoHash, nodeInfo)
            }
        }
    }

    override fun getNodeInfo(nodeInfoHash: String): NodeInfo? = database.transaction { nodeInfoMap[nodeInfoHash] }

    override fun getNodeInfoHashes(): List<String> = database.transaction { nodeInfoMap.keys.toList() }

    override fun getCertificatePath(publicKeyHash: String): CertPath? {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(ByteArray::class.java).run {
                from(CertificateSigningRequest::class.java).run {
                    select(get<CertificateData>(CertificateSigningRequest::certificateData.name).get<ByteArray>(CertificateData::certificatePath.name))
                    where(builder.equal(get<CertificateData>(CertificateSigningRequest::certificateData.name).get<String>(CertificateData::publicKeyHash.name), publicKeyHash))
                }
            }
            session.createQuery(query).uniqueResultOptional().orElseGet { null }?.let { buildCertPath(it) }
        }
    }
}