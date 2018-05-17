/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
                                val maxMessageSize: Int,
                                val auditService: BridgeAuditService,
                                haService: BridgeMasterService,
                                val artemisConnectionService: BridgeArtemisConnectionService,
                                private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeSenderService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null
    private var connectionSubscriber: Subscription? = null
    private var listenerActiveSubscriber: Subscription? = null
    private var bridgeControlListener: BridgeControlListener = BridgeControlListener(conf, conf.outboundConfig!!.socksProxyConfig, maxMessageSize, { ForwardingArtemisMessageClient(artemisConnectionService) })

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
                listenerActiveSubscriber = bridgeControlListener.activeChange.subscribe {
                    stateHelper.active = it
                }
                bridgeControlListener.start()
                auditService.statusChangeEvent("Waiting for activation by at least one bridge control inbox registration")
            } else {
                stateHelper.active = false
                listenerActiveSubscriber?.unsubscribe()
                listenerActiveSubscriber = null
                bridgeControlListener.stop()
            }
        }
    }

    override fun stop() {
        stateHelper.active = false
        listenerActiveSubscriber?.unsubscribe()
        listenerActiveSubscriber = null
        bridgeControlListener.stop()
        connectionSubscriber?.unsubscribe()
        connectionSubscriber = null
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    override fun validateReceiveTopic(topic: String, sourceLegalName: CordaX500Name): Boolean = bridgeControlListener.validateReceiveTopic(topic)
}