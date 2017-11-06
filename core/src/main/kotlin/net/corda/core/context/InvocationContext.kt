package net.corda.core.context

import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import java.security.Principal

// TODO sollecitom docs
@CordaSerializable
data class InvocationContext(val actor: Actor, val origin: Origin, val trace: Trace = Trace(), val externalTrace: Trace? = null) {

    companion object {
        fun rpc(actor: Actor, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.RPC, trace, externalTrace)

        fun peer(actor: Actor, party: CordaX500Name, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.Peer(party), trace, externalTrace)

        fun service(serviceClassName: String, owningLegalIdentity: CordaX500Name, permissions: Set<String>, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(Actor.service(serviceClassName, owningLegalIdentity, permissions), Origin.Service(serviceClassName), trace, externalTrace)

        fun scheduled(actor: Actor, scheduledState: ScheduledStateRef, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.Scheduled(scheduledState), trace, externalTrace)

        fun shell(actor: Actor, trace: Trace = Trace(), externalTrace: Trace? = null): InvocationContext = InvocationContext(actor, Origin.Shell, trace, externalTrace)
    }

    val principal: Principal
        get() = origin.principal(actor)

    val owningLegalIdentity: CordaX500Name
        get() = actor.owningLegalIdentity
}

// TODO sollecitom docs
// TODO sollecitom: consider creating a Permissions / Permission type until we can.
@CordaSerializable
data class Actor(val id: Id, val storeId: StoreId, val owningLegalIdentity: CordaX500Name, val permissions: Set<String>) {

    companion object {
        fun service(serviceClassName: String, owningLegalIdentity: CordaX500Name, permissions: Set<String>): Actor = Actor(Id(serviceClassName), StoreId("SERVICE"), owningLegalIdentity, permissions)
    }

    @CordaSerializable
    data class Id(val value: String)

    // TODO add val legalIdentity: CordaX500Name here (can't do it yet, for this would break the client API)
    @CordaSerializable
    data class StoreId(val value: String)
    // in case we need different user types in corda (to provide polymorphic behaviour) we can an extra field here
}

// TODO sollecitom docs
@CordaSerializable
sealed class Origin {

    abstract fun principal(actor: Actor): Principal

    object RPC : Origin() {

        override fun principal(actor: Actor) = Principal { actor.id.value }
    }

    data class Peer(val party: CordaX500Name) : Origin() {

        override fun principal(actor: Actor) = Principal { party.toString() }
    }

    data class Service(val serviceClassName: String) : Origin() {

        override fun principal(actor: Actor) = Principal { serviceClassName }
    }

    // TODO sollecitom this should use original actor's principal
    data class Scheduled(val scheduledState: ScheduledStateRef) : Origin() {

        override fun principal(actor: Actor) = Principal { "Scheduler" }
    }

    // TODO When proper ssh access enabled, add username/use RPC?
    object Shell : Origin() {

        override fun principal(actor: Actor) = Principal { "Shell User" }
    }
}