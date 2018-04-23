/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.bridging

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort

/**
 * Provides an internal interface that the [BridgeControlListener] delegates to for Bridge activities.
 */
@VisibleForTesting
interface BridgeManager : AutoCloseable {
    fun deployBridge(queueName: String, target: NetworkHostAndPort, legalNames: Set<CordaX500Name>)

    fun destroyBridges(node: NodeInfo)

    fun destroyBridge(queueName: String, hostAndPort: NetworkHostAndPort)

    fun bridgeExists(bridgeName: String): Boolean

    fun start()

    fun stop()
}