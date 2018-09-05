package net.corda.nodeapi.internal.bridging

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.NetworkHostAndPort

/**
 * Provides an internal interface that the [BridgeControlListener] delegates to for Bridge activities.
 */
@VisibleForTesting
interface BridgeManager : AutoCloseable {
    fun deployBridge(queueName: String, targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>)

    fun destroyBridge(queueName: String, targets: List<NetworkHostAndPort>)

    fun start()

    fun stop()
}