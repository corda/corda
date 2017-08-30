package net.corda.core.node

import net.corda.core.flows.type.FlowLogic
import net.corda.core.identity.Party

/**
 * A service hub to be used by the [CordaPluginRegistry]
 */
interface PluginServiceHub : ServiceHub {
    @Deprecated("This is no longer used. Instead annotate the flows produced by your factory with @InitiatedBy and have " +
            "them point to the initiating flow class.", level = DeprecationLevel.ERROR)
    fun registerFlowInitiator(initiatingFlowClass: Class<out FlowLogic<*>>, serviceFlowFactory: (Party) -> FlowLogic<*>) = Unit
}
