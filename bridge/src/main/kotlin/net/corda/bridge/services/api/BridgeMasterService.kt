package net.corda.bridge.services.api

/**
 * This service controls when a bridge may become active and start relaying messages to/from the artemis broker.
 * The active flag is the used to gate dependent services, which should hold off connecting to the bus until this service
 * has been able to become active.
 */
interface BridgeMasterService : ServiceLifecycleSupport {
    // An echo of the active flag that can be used to make the intention of active status checks clearer.
    val isMaster: Boolean get() = active
}