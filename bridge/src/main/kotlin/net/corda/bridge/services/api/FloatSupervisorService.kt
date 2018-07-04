/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.api

/**
 * This is the top level service responsible for creating and managing the [FirewallMode.FloatOuter] portions of the bridge.
 * It exposes a possibly proxied [BridgeAMQPListenerService] component that is used in the [BridgeSupervisorService]
 * to wire up the internal portions of the AMQP peer inbound message path.
 */
interface FloatSupervisorService : ServiceLifecycleSupport {
    val amqpListenerService: BridgeAMQPListenerService
}