package net.corda.nodeapi.internal.bridging

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import org.apache.activemq.artemis.api.core.client.ClientMessage

interface BridgeMetricsService {
    fun bridgeCreated(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>)
    fun bridgeConnected(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>)
    fun packetDropEvent(artemisMessage: ClientMessage, msg: String)
    fun packetAcceptedEvent(sendableMessage: SendableMessage)
    fun bridgeDisconnected(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>)
    fun bridgeDestroyed(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>)
}