package com.r3.corda.networkmanage.common.persistence

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.node.NodeInfo
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
     *  Obtain list of registered node info hashes that haven't been signed yet and have valid certificates.
     */
    fun getUnsignedNodeInfoHashes(): List<SecureHash>

    /**
     * Similar to [getUnsignedNodeInfoHashes] but instead of hashes, map of node info bytes is returned.
     * @return map of node info hashes to their corresponding node info bytes
     */
    fun getUnsignedNodeInfoBytes(): Map<SecureHash, ByteArray>

    /**
     * Retrieve node info using nodeInfo's hash
     * @return [NodeInfo] or null if the node info is not registered.
     */
    fun getNodeInfo(nodeInfoHash: SecureHash): NodeInfo?

    /**
     * Retrieve node info together with its signature using nodeInfo's hash
     * @return [NodeInfo] or null if the node info is not registered.
     */
    fun getSignedNodeInfo(nodeInfoHash: SecureHash): SignedData<NodeInfo>?

    /**
     * The [nodeInfo] is keyed by the public key, old node info with the same public key will be replaced by the new node info.
     * @param nodeInfo node info to be stored
     * @param signature (optional) signature associated with the node info
     * @return hash for the newly created node info entry
     */
    fun putNodeInfo(nodeInfo: NodeInfo, signature: DigitalSignature? = null): SecureHash

    /**
     * Stores the signature for the given node info hash.
     * @param nodeInfoHash node info hash which signature corresponds to
     * @param signature signature for the node info
     */
    fun signNodeInfo(nodeInfoHash: SecureHash, signature: DigitalSignature.WithKey)
}