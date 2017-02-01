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
     * registration is done against a marker [KClass] which is sent in the session handshake by the other party. If this
     * marker class has been registered then the corresponding factory will be used to create the flow which will
     * communicate with the other side. If there is no mapping then the session attempt is rejected.
     * @param markerClass The marker [KClass] present in a session initiation attempt, which is a 1:1 mapping to a [Class]
     * using the <pre>::class</pre> construct. Conventionally this is a [FlowLogic] subclass, however any class can
     * be used, with the default being the class of the initiating flow. This enables the registration to be of the
     * form: registerFlowInitiator(InitiatorFlow::class, ::InitiatedFlow)
     * @param flowFactory The flow factory generating the initiated flow.
     */

    // TODO: remove dependency on Kotlin relfection (Kotlin KClass -> Java Class).
    fun registerFlowInitiator(markerClass: KClass<*>, flowFactory: (Party.Full) -> FlowLogic<*>)

    /**
     * Return the flow factory that has been registered with [markerClass], or null if no factory is found.
     */
    fun getFlowFactory(markerClass: Class<*>): ((Party.Full) -> FlowLogic<*>)?
}
