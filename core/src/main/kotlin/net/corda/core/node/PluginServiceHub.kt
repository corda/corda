package net.corda.core.node

import net.corda.core.crypto.Party
import net.corda.core.flows.FlowFactory
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowVersionInfo
import net.corda.core.node.services.ServiceType
import kotlin.reflect.KClass

/**
 * A service hub to be used by the [CordaPluginRegistry]
 */
interface PluginServiceHub : ServiceHub {
    /**
     * Register the flow factory we wish to use when a initiating party attempts to communicate with us. The
     * registration is done against a marker [Class] (for backwards compatibility with already existing code), however
     * registration information is taken from FlowLogic version annotation. In session handshake sent by other party we
     * obtain general flow name this flow belongs to, because it makes flow version negotiation easier. If this flow name
     * has been registered then the corresponding factory will be used to create the flow
     * which will communicate with the other side. If there is no mapping then the session attempt is rejected.
     * Additionally we get version of this flow for advertising in NetworkMapService (you may want to hide it with [advertise]
     * flag in [FlowVersion]). Assumes that we have one version of this flow and takes it as a default when registering factory.
     * @param markerClass The marker [Class] present in a session initiation attempt, which is a 1:1 mapping to a [Class]
     * using the <pre>::class</pre> construct. It has to be [FlowLogic] subclass. This enables the registration to be of the
     * form: `registerFlowInitiator(InitiatorFlow.class, InitiatedFlow::new)`
     * @param flowFactory The flow factory generating the initiated flow.
     */

    @Deprecated(message = "Use overloaded method which uses Class instead of KClass. This is scheduled for removal in a future release.")
    fun registerFlowInitiator(markerClass: KClass<*>, flowFactory: (Party) -> FlowLogic<*>) {
        registerFlowInitiator(markerClass.java, flowFactory)
    }
    // TODO document both functions API docs
    // TODO: remove dependency on Kotlin relfection (Kotlin KClass -> Java Class).
//    @JvmOverloads // TODO add annotations for Java interop with defaults
    fun registerFlowInitiator(markerClass: Class<*>, flowFactory: (Party) -> FlowLogic<*>,
                              serviceType: ServiceType = ServiceType.corda.getSubType("peer_node"), toAdvertise: Boolean = true) // TODO Override for service tests.

    /**
     * Second option for flow registration, that enables encoding custom version handling (for example preference) and
     * running appropriate FlowLogic version depending on what we got in session handshake.
     */
    //    @JvmOverloads // TODO add annotations for Java interop with defaults
    fun registerFlowInitiator(flowFactory: FlowFactory, serviceType: ServiceType = ServiceType.corda.getSubType("peer_node"),
                              toAdvertise: Boolean = true)

    /**
     * Return the flow factory that has been registered with [markerClass], or null if no factory is found.
     * Uses FlowVersion annotations to figure out the generic flow name this flow belongs to.
     */
    // TODO Kept for backwards compatibility. Remove it.
    fun getFlowFactory(markerClass: Class<*>): ((String, Party) -> FlowLogic<*>?)?

    /**
     * Return the flow factory that has been registered with [flowName], or null if no factory is found.
     */
    fun getFlowFactory(flowName: String): ((String, Party) -> FlowLogic<*>?)?
}
