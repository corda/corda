package net.corda.bridge.services.api

import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.lifecycle.ServiceLifecycleSupport

/**
 * This provides a service to manage connection to the local broker as defined in the [FirewallConfiguration.outboundConfig] section.
 * Once started the service will repeatedly attempt to connect to the bus, signalling success by changing to the [active] state.
 */
interface BridgeArtemisConnectionService : ServiceLifecycleSupport {
    /**
     * When the service becomes [active] this will be non-null and provides access to Artemis management objects.
     */
    val started: ArtemisMessagingClient.Started?

    /**
     * If any errors are experienced with the connection, this method can be used to shutdown the connection, triggering an auto-reconnect.
     */
    fun bounce()
}