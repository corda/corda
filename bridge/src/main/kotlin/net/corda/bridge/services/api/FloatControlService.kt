package net.corda.bridge.services.api

/**
 * This service represent an AMQP socket listener that awaits a remote initiated connection from the [BridgeMode.FloatInner].
 * Only one active connection is allowed at a time and it must match the configured requirements in the [BridgeConfiguration.floatInnerConfig].
 */
interface FloatControlService : ServiceLifecycleSupport