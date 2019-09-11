package net.corda.bridge.services.api

import net.corda.nodeapi.internal.lifecycle.ServiceLifecycleSupport

/**
 * The [BridgeReceiverService] is the service responsible for joining together the perhaps remote [BridgeAMQPListenerService]
 * and the outgoing [IncomingMessageFilterService] that provides the validation and filtering path into the local Artemis broker.
 * It should not become active, or transmit messages until all of the dependencies are themselves active.
 */
interface BridgeReceiverService : ServiceLifecycleSupport {

}