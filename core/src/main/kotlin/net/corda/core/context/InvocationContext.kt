package net.corda.core.context

import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import java.security.Principal

/**
 * Models the information needed to trace an invocation in Corda.
 * Includes initiating actor, origin, trace information, and optional external trace information to correlate clients' IDs.
 */
@CordaSerializable
data class InvocationContext(val actor: Actor, val origin: Origin, val trace: Trace = Trace(), val externalTrace: Trace? = null) {

    companion object {
        fun rpc(actor: Actor, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.RPC, trace, externalTrace)

        fun peer(actor: Actor, party: CordaX500Name, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.Peer(party), trace, externalTrace)

        fun service(serviceClassName: String, owningLegalIdentity: CordaX500Name, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(Actor.service(serviceClassName, owningLegalIdentity), Origin.Service(serviceClassName), trace, externalTrace)

        fun scheduled(actor: Actor, scheduledState: ScheduledStateRef, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.Scheduled(scheduledState), trace, externalTrace)

        fun shell(actor: Actor, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.Shell, trace, externalTrace)
    }

    val principal: Principal
        get() = origin.principal(actor)

    val owningLegalIdentity: CordaX500Name
        get() = actor.owningLegalIdentity
}

/**
 * Models an initiator in Corda, can be a user, a service, etc.
 */
@CordaSerializable
data class Actor(val id: Id, val serviceId: AuthServiceId, val owningLegalIdentity: CordaX500Name) {

    companion object {
        fun service(serviceClassName: String, owningLegalIdentity: CordaX500Name): Actor = Actor(Id(serviceClassName), AuthServiceId("SERVICE"), owningLegalIdentity)
    }

    /**
     * Actor id.
     */
    @CordaSerializable
    data class Id(val value: String)

}

/**
 * Invocation origin for tracing purposes.
 */
@CordaSerializable
sealed class Origin {

    /**
     * Returns the [Principal] for a given [Actor].
     */
    abstract fun principal(actor: Actor): Principal

    /**
     * Origin was an RPC call.
     */
    object RPC : Origin() {

        override fun principal(actor: Actor) = Principal { actor.id.value }
    }

    /**
     * Origin was a message sent by a [Peer].
     */
    data class Peer(val party: CordaX500Name) : Origin() {

        override fun principal(actor: Actor) = Principal { party.toString() }
    }

    /**
     * Origin was a Corda Service.
     */
    data class Service(val serviceClassName: String) : Origin() {

        override fun principal(actor: Actor) = Principal { serviceClassName }
    }

    /**
     * Origin was a scheduled activity.
     */
    data class Scheduled(val scheduledState: ScheduledStateRef) : Origin() {

        override fun principal(actor: Actor) = Principal { "Scheduler" }
    }

    // TODO When proper ssh access enabled, add username/use RPC?
    /**
     * Origin was the Shell.
     */
    object Shell : Origin() {

        override fun principal(actor: Actor) = Principal { "Shell User" }
    }
}