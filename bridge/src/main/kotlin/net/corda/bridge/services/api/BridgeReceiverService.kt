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
 * The [BridgeReceiverService] is the service responsible for joining together the perhaps remote [BridgeAMQPListenerService]
 * and the outgoing [IncomingMessageFilterService] that provides the validation and filtering path into the local Artemis broker.
 * It should not become active, or transmit messages until all of the dependencies are themselves active.
 */
interface BridgeReceiverService : ServiceLifecycleSupport {

}