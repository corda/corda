package net.corda.bridge.services.api

/**
 * This is the top level service responsible for creating and managing the [FirewallMode.FloatOuter] portions of the bridge.
 * It exposes a possibly proxied [BridgeAMQPListenerService] component that is used in the [BridgeSupervisorService]
 * to wire up the internal portions of the AMQP peer inbound message path.
 */
interface FloatSupervisorService : ServiceLifecycleSupport {
    val amqpListenerService: BridgeAMQPListenerService
}