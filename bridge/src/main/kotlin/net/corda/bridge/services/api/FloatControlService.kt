package net.corda.bridge.services.api

import net.corda.nodeapi.internal.lifecycle.ServiceLifecycleSupport

/**
 * This service represent an AMQP socket listener that awaits a remote initiated connection from the [FirewallMode.BridgeInner].
 * Only one active connection is allowed at a time and it must match the configured requirements in the [FirewallConfiguration.bridgeInnerConfig].
 */
interface FloatControlService : ServiceLifecycleSupport