package com.r3.corda.networkmanage.common.persistence

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.node.NodeInfo
import net.corda.nodeapi.internal.SignedNodeInfo
import java.security.cert.CertPath

/**
 * Data access object interface for NetworkMap/NodeInfo persistence layer
 */
interface NodeInfoStorage {
    /**
     *  Retrieve node certificate path using the node public key hash.
     *  @return [CertPath] or null if the public key is not registered with the Doorman.
     */
    fun getCertificatePath(publicKeyHash: SecureHash): CertPath?

    /**
     * Retrieve node info together with its signature using nodeInfo's hash
     * @return [NodeInfo] or null if the node info is not registered.
     */
    fun getNodeInfo(nodeInfoHash: SecureHash): SignedNodeInfo?

    /**
     * The [nodeInfo] is keyed by the public key, old node info with the same public key will be replaced by the new node info.
     * @param signedNodeInfo signed node info data to be stored
     * @return hash for the newly created node info entry
     */
    fun putNodeInfo(signedNodeInfo: SignedNodeInfo): SecureHash
}