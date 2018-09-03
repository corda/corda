package net.corda.bridge.services.audit

import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import java.net.InetSocketAddress

class LoggingFirewallAuditService(val conf: FirewallConfiguration,
                                  private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : FirewallAuditService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    override fun start() {
        stateHelper.active = true
    }

    override fun stop() {
        stateHelper.active = false
    }

    override fun successfulConnectionEvent(inbound: Boolean, sourceIP: InetSocketAddress, certificateSubject: String, msg: String) {
        log.info(msg)
    }

    override fun failedConnectionEvent(inbound: Boolean, sourceIP: InetSocketAddress?, certificateSubject: String?, msg: String) {
        log.warn(msg)
    }

    override fun packetDropEvent(packet: ReceivedMessage?, msg: String) {
        log.info(msg)
    }

    override fun packetAcceptedEvent(packet: ReceivedMessage) {
        log.trace { "Packet received from ${packet.sourceLegalName} uuid: ${packet.applicationProperties["_AMQ_DUPL_ID"]}" }
    }

    override fun statusChangeEvent(msg: String) {
        log.info(msg)
    }
}