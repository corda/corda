package net.corda.bridge.services.api

/**
 * This is the top level service representing the [BridgeMode.FloatInner] service stack. The primary role of this component is to
 * create and wire up concrete implementations of the relevant services according to the [BridgeConfiguration] details.
 * The possibly proxied path to the [BridgeAMQPListenerService] is typically a constructor input
 * as that is a [BridgeMode.FloatOuter] component.
 */
interface BridgeSupervisorService : ServiceLifecycleSupport