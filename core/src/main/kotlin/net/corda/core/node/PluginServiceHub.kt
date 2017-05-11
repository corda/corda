package net.corda.core.node

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

/**
 * A service hub to be used by the [CordaPluginRegistry]
 */
interface PluginServiceHub : ServiceHub {
    /**
     * Register the service flow factory to use when an initiating party attempts to communicate with us. The registration
     * is done against the [Class] object of the client flow to the service flow. What this means is if a counterparty
     * starts a [FlowLogic] represented by [initiatingFlowClass] and starts communication with us, we will execute the service
     * flow produced by [serviceFlowFactory]. This service flow has respond correctly to the sends and receives the client
     * does.
     * @param initiatingFlowClass [Class] of the client flow involved in this client-server communication.
     * @param serviceFlowFactory Lambda which produces a new service flow for each new client flow communication. The
     * [Party] parameter of the factory is the client's identity.
     * @throws IllegalArgumentException If [initiatingFlowClass] is not annotated with [net.corda.core.flows.InitiatingFlow].
     */
    fun registerServiceFlow(initiatingFlowClass: Class<out FlowLogic<*>>, serviceFlowFactory: (Party) -> FlowLogic<*>)

    @Suppress("UNCHECKED_CAST")
    @Deprecated("This is scheduled to be removed in a future release", ReplaceWith("registerServiceFlow"))
    fun registerFlowInitiator(markerClass: Class<*>, flowFactory: (Party) -> FlowLogic<*>) {
        registerServiceFlow(markerClass as Class<out FlowLogic<*>>, flowFactory)
    }
}
