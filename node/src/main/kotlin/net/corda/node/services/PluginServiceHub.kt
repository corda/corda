package net.corda.node.services

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.node.services.config.NodeConfiguration
import org.jetbrains.exposed.sql.Database

/**
 * A service hub to be used by the [CordaPluginRegistry]
 */
interface PluginServiceHub : ServiceHub {
    @Deprecated("This is no longer used. Instead annotate the flows produced by your factory with @InitiatedBy and have " +
            "them point to the initiating flow class.", level = DeprecationLevel.ERROR)
    fun registerFlowInitiator(initiatingFlowClass: Class<out FlowLogic<*>>, serviceFlowFactory: (Party) -> FlowLogic<*>) = Unit

    val db: Database
    val config: NodeConfiguration
}
