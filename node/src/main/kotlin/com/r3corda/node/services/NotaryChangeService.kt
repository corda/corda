package com.r3corda.node.services

import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.PluginServiceHub
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.NotaryChangeProtocol

object NotaryChange {
    class Plugin : CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    /**
     * A service that monitors the network for requests for changing the notary of a state,
     * and immediately runs the [NotaryChangeProtocol] if the auto-accept criteria are met.
     */
    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {
        init {
            services.registerProtocolInitiator(NotaryChangeProtocol.Instigator::class) { NotaryChangeProtocol.Acceptor(it) }
        }
    }
}
