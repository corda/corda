package net.corda.nodeapi.internal.bridging

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.NetworkHostAndPort
import org.apache.activemq.artemis.api.core.client.ClientMessage

/**
 * Provides an internal interface that the [BridgeControlListener] delegates to for Bridge activities.
 */
@VisibleForTesting
interface BridgeManager : AutoCloseable {
    fun deployBridge(sourceX500Name: String, queueName: String, targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>)

    fun destroyBridge(queueName: String, targets: List<NetworkHostAndPort>)

    fun start()

    fun stop()
}

fun ClientMessage.payload() = ByteArray(bodySize).apply { bodyBuffer.readBytes(this) }