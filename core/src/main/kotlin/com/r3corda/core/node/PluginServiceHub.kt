package com.r3corda.core.node

import com.r3corda.core.crypto.Party
import com.r3corda.core.protocols.ProtocolLogic
import kotlin.reflect.KClass

/**
 * A service hub to be used by the [CordaPluginRegistry]
 */
interface PluginServiceHub : ServiceHub {
    /**
     * Register the protocol factory we wish to use when a initiating party attempts to communicate with us. The
     * registration is done against a marker [KClass] which is sent in the session handshake by the other party. If this
     * marker class has been registered then the corresponding factory will be used to create the protocol which will
     * communicate with the other side. If there is no mapping then the session attempt is rejected.
     * @param markerClass The marker [KClass] present in a session initiation attempt, which is a 1:1 mapping to a [Class]
     * using the <pre>::class</pre> construct. Conventionally this is a [ProtocolLogic] subclass, however any class can
     * be used, with the default being the class of the initiating protocol. This enables the registration to be of the
     * form: registerProtocolInitiator(InitiatorProtocol::class, ::InitiatedProtocol)
     * @param protocolFactory The protocol factory generating the initiated protocol.
     */

    // TODO: remove dependency on Kotlin relfection (Kotlin KClass -> Java Class).
    fun registerProtocolInitiator(markerClass: KClass<*>, protocolFactory: (Party) -> ProtocolLogic<*>)

    /**
     * Return the protocol factory that has been registered with [markerClass], or null if no factory is found.
     */
    fun getProtocolFactory(markerClass: Class<*>): ((Party) -> ProtocolLogic<*>)?
}
