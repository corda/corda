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

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.bridging.BridgeControlListener

/**
 * This service is responsible for the outgoing path of messages from the local Artemis broker
 * to the remote peer using AMQP. It should not become active until the connection to the local Artemis broker is stable
 * and the [BridgeMasterService] has allowed this bridge instance to become activated.
 * In practice the actual AMQP bridging logic is carried out using an instance of the [BridgeControlListener] class with
 * lifecycle support coming from the service.
 */
interface BridgeSenderService : ServiceLifecycleSupport {
    /**
     * This method is used to check inbound packets against the list of valid inbox addresses registered from the nodes
     * via the local Bridge Control Protocol. They may optionally also check this against the source legal name.
     */
    fun validateReceiveTopic(topic: String, sourceLegalName: CordaX500Name): Boolean
}