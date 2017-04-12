package net.corda.node.services

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.flows.NotaryChangeFlow

class NotaryChangePlugin : CordaPluginRegistry() {
    override fun initialise(serviceHub: PluginServiceHub) {
        serviceHub.registerFlowInitiator(NotaryChangeFlow.Instigator::class.java) { NotaryChangeFlow.Acceptor(it) }
    }
}
