/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.supervisors

import net.corda.bridge.services.api.*
import net.corda.bridge.services.receiver.BridgeAMQPListenerServiceImpl
import net.corda.bridge.services.receiver.FloatControlListenerService
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import org.slf4j.LoggerFactory
import rx.Subscription

class FloatSupervisorServiceImpl(val conf: BridgeConfiguration,
                                 val maxMessageSize: Int,
                                 val auditService: BridgeAuditService,
                                 private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : FloatSupervisorService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
        val consoleLogger = LoggerFactory.getLogger("BasicInfo")
    }

    override val amqpListenerService: BridgeAMQPListenerService
    private val floatControlService: FloatControlService?
    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null

    init {
        amqpListenerService = BridgeAMQPListenerServiceImpl(conf, maxMessageSize, auditService)
        floatControlService = if (conf.bridgeMode == BridgeMode.FloatOuter) {
            require(conf.haConfig == null) { "Float process should not have HA config, that is controlled via the bridge." }
            FloatControlListenerService(conf, maxMessageSize, auditService, amqpListenerService)
        } else {
            null
        }
        statusFollower = ServiceStateCombiner(listOf(amqpListenerService, floatControlService).filterNotNull())
        activeChange.subscribe {
            consoleLogger.info("FloatSupervisorService: active = $it")
        }
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe {
            stateHelper.active = it
        }
        amqpListenerService.start()
        floatControlService?.start()
    }

    override fun stop() {
        stateHelper.active = false
        floatControlService?.stop()
        amqpListenerService.stop()
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }
}