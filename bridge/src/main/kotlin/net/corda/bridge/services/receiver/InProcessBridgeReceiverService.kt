package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.*
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.internal.readAll
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import rx.Subscription

class InProcessBridgeReceiverService(val conf: FirewallConfiguration,
                                     val auditService: FirewallAuditService,
                                     haService: BridgeMasterService,
                                     val amqpListenerService: BridgeAMQPListenerService,
                                     val filterService: IncomingMessageFilterService,
                                     private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeReceiverService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null
    private var receiveSubscriber: Subscription? = null
    private val sslConfiguration: MutualSslConfiguration

    init {
        statusFollower = ServiceStateCombiner(listOf(auditService, haService, amqpListenerService, filterService))
        sslConfiguration = conf.inboundConfig?.customSSLConfiguration ?: conf.p2pSslOptions
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({
            if (it) {
                val keyStoreBytes = sslConfiguration.keyStore.path.readAll()
                val trustStoreBytes = sslConfiguration.trustStore.path.readAll()
                amqpListenerService.provisionKeysAndActivate(keyStoreBytes,
                        sslConfiguration.keyStore.password.toCharArray(),
                        sslConfiguration.keyStore.password.toCharArray(),
                        trustStoreBytes,
                        sslConfiguration.trustStore.password.toCharArray())
            } else {
                if (amqpListenerService.running) {
                    amqpListenerService.wipeKeysAndDeactivate()
                }
            }
            stateHelper.active = it
        }, { log.error("Error in state change", it) })
        receiveSubscriber = amqpListenerService.onReceive.subscribe({
            processMessage(it)
        }, { log.error("Error in state change", it) })
    }

    private fun processMessage(receivedMessage: ReceivedMessage) {
        filterService.sendMessageToLocalBroker(receivedMessage)
    }

    override fun stop() {
        stateHelper.active = false
        if (amqpListenerService.running) {
            amqpListenerService.wipeKeysAndDeactivate()
        }
        receiveSubscriber?.unsubscribe()
        receiveSubscriber = null
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }
}