package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.*
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import rx.Subscription

class InProcessBridgeReceiverService(auditService: FirewallAuditService,
                                     haService: BridgeMasterService,
                                     private val signingService: TLSSigningService,
                                     private val amqpListenerService: BridgeAMQPListenerService,
                                     private val filterService: IncomingMessageFilterService,
                                     private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeReceiverService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    private val statusFollower = ServiceStateCombiner(listOf(auditService, haService, amqpListenerService, filterService, signingService))
    private var statusSubscriber: Subscription? = null
    private var receiveSubscriber: Subscription? = null

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({
            if (it) {
                amqpListenerService.provisionKeysAndActivate(signingService.keyStore(), signingService.truststore())
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