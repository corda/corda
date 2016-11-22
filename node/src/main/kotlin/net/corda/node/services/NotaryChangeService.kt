package net.corda.node.services

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.flows.NotaryChangeFlow

object NotaryChange {
    class Plugin : CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    /**
     * A service that monitors the network for requests for changing the notary of a state,
     * and immediately runs the [NotaryChangeFlow] if the auto-accept criteria are met.
     */
    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {
        init {
            services.registerFlowInitiator(NotaryChangeFlow.Instigator::class) { NotaryChangeFlow.Acceptor(it) }
        }
    }
}
