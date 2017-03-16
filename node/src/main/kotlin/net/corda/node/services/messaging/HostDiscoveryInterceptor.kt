package net.corda.node.services.messaging

import net.corda.node.services.network.NetworkMapService
import org.apache.activemq.artemis.api.core.Interceptor
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.core.protocol.core.Packet
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection

/**
 * Intercepts session messages containing the public address discovery topic and
 * and adds a property specifying the origin host.
 */
class HostDiscoveryInterceptor : Interceptor {
    override fun intercept(packet: Packet, connection: RemotingConnection): Boolean {
        if (packet is SessionSendMessage && isHostDiscovery(packet.message)) {
            val remoteHost = extractHost(connection.remoteAddress)
            packet.message.putStringProperty("originHost", remoteHost)
        }
        return true
    }

    private fun isHostDiscovery(message: Message) = message.getStringProperty(NodeMessagingClient.TOPIC_PROPERTY) == NetworkMapService.DISCOVER_HOST_TOPIC
    // TODO: handle IP6
    private fun extractHost(address: String) = address.split("/", ":")[1]
}