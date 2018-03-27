package net.corda.core.flows

import net.corda.core.context.Actor
import net.corda.core.context.AuthServiceId
import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.annotations.serialization.CordaSerializable
import java.security.Principal

/**
 * Please note that [FlowInitiator] has been superceded by [net.corda.core.context.InvocationContext], which offers
 * more detail for the same event.
 *
 * FlowInitiator holds information on who started the flow. We have different ways of doing that: via [FlowInitiator.RPC],
 * communication started by peer nodes ([FlowInitiator.Peer]), scheduled flows ([FlowInitiator.Scheduled])
 * or via the Corda Shell ([FlowInitiator.Shell]).
 */
@CordaSerializable
sealed class FlowInitiator : Principal {
    /** Started using [net.corda.core.messaging.CordaRPCOps.startFlowDynamic]. */
    data class RPC(val username: String) : FlowInitiator() {
        override fun getName(): String = username
    }

    /** Started when we get new session initiation request. */
    data class Peer(val party: Party) : FlowInitiator() {
        override fun getName(): String = party.name.toString()
    }

    /** Started by a CordaService. */
    data class Service(val serviceClassName: String) : FlowInitiator() {
        override fun getName(): String = serviceClassName
    }

    /** Started as scheduled activity. */
    data class Scheduled(val scheduledState: ScheduledStateRef) : FlowInitiator() {
        override fun getName(): String = "Scheduler"
    }

    // TODO When proper ssh access enabled, add username/use RPC?
    object Shell : FlowInitiator() {
        override fun getName(): String = "Shell User"
    }

    /**
     * Returns an [InvocationContext], which is equivalent to this object but expressed using the successor to this
     * class hierarchy (which is now deprecated). The returned object has less information than it could have, so
     * prefer to use fetch an invocation context directly if you can (e.g. in [net.corda.core.messaging.StateMachineInfo])
     */
    val invocationContext: InvocationContext get() {
        val unknownName = CordaX500Name("UNKNOWN", "UNKNOWN", "GB")
        var actor: Actor? = null
        val origin: InvocationOrigin
        when (this) {
            is FlowInitiator.RPC -> {
                actor = Actor(Actor.Id(this.username), AuthServiceId("UNKNOWN"), unknownName)
                origin = InvocationOrigin.RPC(actor)
            }
            is FlowInitiator.Peer -> origin = InvocationOrigin.Peer(this.party.name)
            is FlowInitiator.Service -> origin = InvocationOrigin.Service(this.serviceClassName, unknownName)
            FlowInitiator.Shell -> origin = InvocationOrigin.Shell
            is FlowInitiator.Scheduled -> origin = InvocationOrigin.Scheduled(this.scheduledState)
        }
        return InvocationContext.newInstance(origin = origin, actor = actor)
    }
}