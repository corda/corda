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

import com.r3.corda.networkmanage.common.persistence.entity.ParametersUpdateEntity
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NodeInfo
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import java.security.PublicKey
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
     * Returns the parameters update the node has accepted or null if couldn't find node info with given hash or
     * there is no information on accepted parameters update stored for this entity.
     */
    fun getAcceptedParametersUpdate(nodeInfoHash: SecureHash): ParametersUpdateEntity?

    /**
     * The [nodeInfoAndSigned] is keyed by the public key, old node info with the same public key will be replaced by the new node info.
     * If republishing of the same nodeInfo happens, then we will record the time it was republished in the database.
     * Based on that information we can remove unresponsive nodes from network (event horizon is the parameter that tells how
     * long node can be down before it gets removed). If the nodes becomes active again, it will enter back to the network map
     * after republishing its [NodeInfo].
     * @param nodeInfoAndSigned signed node info data to be stored
     * @return hash for the newly created node info entry
     */
    fun putNodeInfo(nodeInfoAndSigned: NodeInfoAndSigned): SecureHash

    /**
     * Store information about latest accepted [NetworkParameters] hash.
     * @param publicKey Public key that accepted network parameters. This public key should belong to [NodeInfo]
     * @param acceptedParametersHash Hash of latest accepted network parameters.
     */
    fun ackNodeInfoParametersUpdate(publicKey: PublicKey, acceptedParametersHash: SecureHash)
}