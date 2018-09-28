package net.corda.nodeapi.internal.bridging

import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import org.apache.activemq.artemis.api.core.client.ClientMessage

interface BridgeAuditService {
    fun packetDropEvent(artemisMessage: ClientMessage, msg: String)
    fun packetAcceptedEvent(sendableMessage: SendableMessage)
}