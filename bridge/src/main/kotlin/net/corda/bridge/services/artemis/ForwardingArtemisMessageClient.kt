package net.corda.bridge.services.artemis

import net.corda.bridge.services.api.BridgeArtemisConnectionService
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisSessionProvider

class ForwardingArtemisMessageClient(val artemisConnectionService: BridgeArtemisConnectionService) : ArtemisSessionProvider {
    override fun start(): ArtemisMessagingClient.Started {
        // We don't want to start and stop artemis from clients as the lifecycle management is provided by the BridgeArtemisConnectionService
        return artemisConnectionService.started!!
    }

    override fun stop() {
        // We don't want to start and stop artemis from clients as the lifecycle management is provided by the BridgeArtemisConnectionService
    }

    override val started: ArtemisMessagingClient.Started?
        get() = artemisConnectionService.started

}