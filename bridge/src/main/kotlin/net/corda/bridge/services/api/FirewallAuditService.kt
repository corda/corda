package net.corda.bridge.services.api

import net.corda.nodeapi.internal.protonwrapper.messages.ApplicationMessage
import java.net.InetSocketAddress

/**
 * This service provides centralised facilities for recording business critical events in the bridge.
 * Currently the simple implementation just records events to log file, but future implementations may need to post
 * security data to an enterprise service.
 */
interface FirewallAuditService : ServiceLifecycleSupport {
    fun successfulConnectionEvent(address: InetSocketAddress, certificateSubject: String, msg: String, direction: RoutingDirection)
    fun failedConnectionEvent(address: InetSocketAddress, certificateSubject: String?, msg: String, direction: RoutingDirection)
    fun packetDropEvent(packet: ApplicationMessage?, msg: String, direction: RoutingDirection)
    fun packetAcceptedEvent(packet: ApplicationMessage, direction: RoutingDirection)
    fun statusChangeEvent(msg: String)
}

/**
 * Specifies direction of message flow with regard to Corda Node connected to Firewall.
 */
enum class RoutingDirection {
    INBOUND,
    OUTBOUND
}