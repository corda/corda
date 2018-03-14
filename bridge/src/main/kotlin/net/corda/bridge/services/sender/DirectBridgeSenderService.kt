package net.corda.bridge.services.sender

import net.corda.bridge.services.api.*
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.bridging.BridgeControlListener
import rx.Subscription

class DirectBridgeSenderService(val conf: BridgeConfiguration,
                                val auditService: BridgeAuditService,
                                val haService: BridgeMasterService,
                                val artemisConnectionService: BridgeArtemisConnectionService,
                                private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeSenderService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null
    private var connectionSubscriber: Subscription? = null
    private var bridgeControlListener: BridgeControlListener = BridgeControlListener(conf, { ForwardingArtemisMessageClient(artemisConnectionService) })

    init {
        statusFollower = ServiceStateCombiner(listOf(auditService, artemisConnectionService, haService))
    }

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

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe { ready ->
            if (ready) {
                bridgeControlListener.start()
                stateHelper.active = true
            } else {
                stateHelper.active = false
                bridgeControlListener.stop()
            }
        }
    }

    override fun stop() {
        stateHelper.active = false
        bridgeControlListener.stop()
        connectionSubscriber?.unsubscribe()
        connectionSubscriber = null
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    override fun validateReceiveTopic(topic: String, sourceLegalName: CordaX500Name): Boolean = bridgeControlListener.validateReceiveTopic(topic)
}