package com.r3.corda.doorman.persistence

import net.corda.core.node.NodeInfo
import java.security.cert.CertPath
import javax.persistence.*

interface NodeInfoStorage {
    /**
     *  Retrieve certificate paths using the public key hash.
     *  @return [CertPath] or null if the public key is not registered with the Doorman.
     */
    fun getCertificatePath(publicKeyHash: String): CertPath?

    /**
     *  Obtain list of registered node info hashes.
     */
    //TODO: we might want to return [SecureHash] instead of String
    fun getNodeInfoHashes(): List<String>

    /**
     * Retrieve node info using nodeInfo's hash
     * @return [NodeInfo] or null if the node info is not registered.
     */
    fun getNodeInfo(nodeInfoHash: String): NodeInfo?

    /**
     * The [nodeInfo] is keyed by the public key, old node info with the same public key will be replaced by the new node info.
     */
    fun putNodeInfo(nodeInfo: NodeInfo)
}

@Entity
@Table(name = "node_info")
class NodeInfoEntity(
        @Id
        @Column(name = "node_info_hash", length = 64)
        var nodeInfoHash: String = "",

        @Lob
        @Column(name = "node_info")
        var nodeInfo: ByteArray = ByteArray(0)
)

@Entity
@Table(name = "public_key_node_info_link")
class PublicKeyNodeInfoLink(
        @Id
        @Column(name = "public_key_hash", length = 64)
        var publicKeyHash: String = "",

        @Column(name = "node_info_hash", length = 64)
        var nodeInfoHash: String = ""
)