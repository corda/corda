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