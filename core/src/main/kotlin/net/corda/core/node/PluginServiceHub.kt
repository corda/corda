package net.corda.core.node

import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import kotlin.reflect.KClass

/**
 * A service hub to be used by the [CordaPluginRegistry]
 */
interface PluginServiceHub : ServiceHub {
    /**
     * Register the flow factory we wish to use when a initiating party attempts to communicate with us. The
     * registration is done against a marker [Class] which is sent in the session handshake by the other party. If this
     * marker class has been registered then the corresponding factory will be used to create the flow which will
     * communicate with the other side. If there is no mapping then the session attempt is rejected.
     * @param markerClass The marker [Class] present in a session initiation attempt. Conventionally this is a [FlowLogic]
     * subclass, however any class can be used, with the default being the class of the initiating flow. This enables
     * the registration to be of the form: `registerFlowInitiator(InitiatorFlow.class, InitiatedFlow::new)`
     * @param flowFactory The flow factory generating the initiated flow.
     */
    fun registerFlowInitiator(markerClass: Class<*>, flowFactory: (Party) -> FlowLogic<*>)

    @Deprecated(message = "Use overloaded method which uses Class instead of KClass. This is scheduled for removal in a future release.")
    fun registerFlowInitiator(markerClass: KClass<*>, flowFactory: (Party) -> FlowLogic<*>) {
        registerFlowInitiator(markerClass.java, flowFactory)
    }

    /**
     * Return the flow factory that has been registered with [markerClass], or null if no factory is found.
     */
    fun getFlowFactory(markerClass: Class<*>): ((Party) -> FlowLogic<*>)?
}
