package com.r3corda.node.services

import com.r3corda.core.messaging.Ack
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.AbstractStateReplacementProtocol
import com.r3corda.protocols.NotaryChangeProtocol


object NotaryChange {
    class Plugin : CordaPluginRegistry {
        override val webApis: List<Class<*>> = emptyList()
        override val requiredProtocols: Map<String, Set<String>> = emptyMap()
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    /**
     * A service that monitors the network for requests for changing the notary of a state,
     * and immediately runs the [NotaryChangeProtocol] if the auto-accept criteria are met.
     */
    class Service(val services: ServiceHubInternal) : AbstractNodeService(services.networkService, services.networkMapCache) {
        init {
            addMessageHandler(NotaryChangeProtocol.TOPIC,
                    { req: AbstractStateReplacementProtocol.Handshake -> handleChangeNotaryRequest(req) }
            )
        }

        private fun handleChangeNotaryRequest(req: AbstractStateReplacementProtocol.Handshake): Ack {
            val protocol = NotaryChangeProtocol.Acceptor(
                    req.replyToParty,
                    req.sessionID,
                    req.sessionIdForSend)
            services.startProtocol(NotaryChangeProtocol.TOPIC, protocol)
            return Ack
        }
    }
}
