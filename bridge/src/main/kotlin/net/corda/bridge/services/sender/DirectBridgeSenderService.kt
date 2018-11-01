package net.corda.bridge.services.sender

import net.corda.bridge.services.api.*
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.bridging.BridgeControlListener
import net.corda.nodeapi.internal.bridging.BridgeMetricsService
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import org.apache.activemq.artemis.api.core.client.ClientMessage
import rx.Subscription
import java.net.InetSocketAddress

class DirectBridgeSenderService(val conf: FirewallConfiguration,
                                val maxMessageSize: Int,
                                val auditService: FirewallAuditService,
                                haService: BridgeMasterService,
                                private val artemisConnectionService: BridgeArtemisConnectionService,
                                private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeSenderService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()
    }

    private val statusFollower: ServiceStateCombiner = ServiceStateCombiner(listOf(auditService, artemisConnectionService, haService))
    private var statusSubscriber: Subscription? = null
    private var listenerActiveSubscriber: Subscription? = null
    private var bridgeControlListener = BridgeControlListener(conf.p2pSslOptions,
            conf.outboundConfig!!.socksProxyConfig,
            maxMessageSize,
            conf.bridgeInnerConfig?.enableSNI ?: true,
            { ForwardingArtemisMessageClient(artemisConnectionService) },
            BridgeAuditServiceAdaptor(auditService))

    private class ForwardingArtemisMessageClient(val artemisConnectionService: BridgeArtemisConnectionService) : ArtemisSessionProvider {
        override fun start(): ArtemisMessagingClient.Started {
            // We don't want to start and stop artemis from clients as the lifecycle management is provided by the BridgeArtemisConnectionService
            return artemisConnectionService.started!!
        }

        override fun stop() {
            // We don't want to start and stop artemis from clients as the lifecycle management is provided by the BridgeArtemisConnectionService
        }

        override val started: ArtemisMessagingClient.Started?
            get() = artemisConnectionService.started

    }

    private class BridgeAuditServiceAdaptor(private val auditService: FirewallAuditService) : BridgeMetricsService {
        override fun bridgeCreated(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
            // No corresponding method on FirewallAuditService yet
        }

        override fun bridgeConnected(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
            val firstHostPort = targets.first()
            auditService.successfulConnectionEvent(InetSocketAddress(firstHostPort.host, firstHostPort.port),
                    legalNames.first().toString(), "BridgeConnected", RoutingDirection.OUTGOING)
        }

        override fun bridgeDisconnected(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
            // No corresponding method on FirewallAuditService yet
        }

        override fun bridgeDestroyed(targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
            // No corresponding method on FirewallAuditService yet
        }

        override fun packetDropEvent(artemisMessage: ClientMessage, msg: String) {
            // Too much of a hassle to translate `ClientMessage` into `ApplicationMessage?`, especially given that receiving side is likely
            // to be doing counting only.
            auditService.packetDropEvent(null, msg, RoutingDirection.OUTGOING)
        }

        override fun packetAcceptedEvent(sendableMessage: SendableMessage) {
            auditService.packetAcceptedEvent(sendableMessage, RoutingDirection.OUTGOING)
        }
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({ ready ->
            if (ready) {
                listenerActiveSubscriber = bridgeControlListener.activeChange.subscribe({
                    stateHelper.active = it
                }, { log.error("Bridge event error", it) })
                bridgeControlListener.start()
                auditService.statusChangeEvent("Waiting for activation by at least one bridge control inbox registration")
            } else {
                stateHelper.active = false
                listenerActiveSubscriber?.unsubscribe()
                listenerActiveSubscriber = null
                bridgeControlListener.stop()
            }
        }, { log.error("Error in state change", it) })
    }

    override fun stop() {
        stateHelper.active = false
        listenerActiveSubscriber?.unsubscribe()
        listenerActiveSubscriber = null
        bridgeControlListener.stop()
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    override fun validateReceiveTopic(topic: String, sourceLegalName: CordaX500Name): Boolean = bridgeControlListener.validateReceiveTopic(topic)
}