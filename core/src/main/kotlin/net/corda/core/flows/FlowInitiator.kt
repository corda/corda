package net.corda.core.flows

import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.Principal

/**
 * FlowInitiator holds information on who started the flow. We have different ways of doing that: via RPC [FlowInitiator.RPC],
 * communication started by peer node [FlowInitiator.Peer], scheduled flows [FlowInitiator.Scheduled]
 * or via the Corda Shell [FlowInitiator.Shell].
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
}