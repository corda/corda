package com.r3corda.node.services

import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.AbstractStateReplacementProtocol
import com.r3corda.protocols.NotaryChangeProtocol
import com.r3corda.protocols.NotaryChangeProtocol.TOPIC

object NotaryChange {
    class Plugin : CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    /**
     * A service that monitors the network for requests for changing the notary of a state,
     * and immediately runs the [NotaryChangeProtocol] if the auto-accept criteria are met.
     */
    class Service(services: ServiceHubInternal) : AbstractNodeService(services) {
        init {
            addProtocolHandler(TOPIC, TOPIC) { req: AbstractStateReplacementProtocol.Handshake ->
                NotaryChangeProtocol.Acceptor(req.replyToParty)
            }
        }
    }
}
