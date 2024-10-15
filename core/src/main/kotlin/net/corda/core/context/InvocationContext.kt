package net.corda.core.context

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.telemetry.SerializedTelemetry
import net.corda.core.serialization.CordaSerializable
import java.security.Principal

/**
 * Models the information needed to trace an invocation in Corda.
 * Includes initiating actor, origin, trace information, and optional external trace information to correlate clients' IDs.
 *
 * @property origin Origin of the invocation.
 * @property trace Corda invocation trace.
 * @property actor Acting agent of the invocation, used to derive the security principal.
 * @property externalTrace Optional external invocation trace for cross-system logs correlation.
 * @property impersonatedActor Optional impersonated actor, used for logging but not for authorisation.
 */
@CordaSerializable
data class InvocationContext(
    val origin: InvocationOrigin,
    val trace: Trace,
    val actor: Actor?,
    val externalTrace: Trace? = null,
    val impersonatedActor: Actor? = null,
    val arguments: List<Any?>? = emptyList(), // 'arguments' is nullable so that a - >= 4.6 version - RPC client can be backwards compatible against - < 4.6 version - nodes
    val clientId: String? = null,
    val serializedTelemetry: SerializedTelemetry? = null
) {

    constructor(
        origin: InvocationOrigin,
        trace: Trace,
        actor: Actor?,
        externalTrace: Trace? = null,
        impersonatedActor: Actor? = null
    ) : this(origin, trace, actor, externalTrace, impersonatedActor, emptyList())

    constructor(
            origin: InvocationOrigin,
            trace: Trace,
            actor: Actor?,
            externalTrace: Trace? = null,
            impersonatedActor: Actor? = null,
            arguments: List<Any?>? = emptyList(),
            clientId: String? = null
    ) : this(origin, trace, actor, externalTrace, impersonatedActor, arguments, clientId, serializedTelemetry = null)

    companion object {
        /**
         * Creates an [InvocationContext] with a [Trace] that defaults to a [java.util.UUID] as value and [java.time.Instant.now] timestamp.
         */
        @DeleteForDJVM
        @JvmStatic
        @JvmOverloads
        @Suppress("LongParameterList")
        fun newInstance(
            origin: InvocationOrigin,
            trace: Trace = Trace.newInstance(),
            actor: Actor? = null,
            externalTrace: Trace? = null,
            impersonatedActor: Actor? = null,
            arguments: List<Any?> = emptyList(),
            clientId: String? = null,
            serializedTelemetry: SerializedTelemetry? = null
        ) = InvocationContext(origin, trace, actor, externalTrace, impersonatedActor, arguments, clientId, serializedTelemetry)

        /**
         * Creates an [InvocationContext] with [InvocationOrigin.RPC] origin.
         */
        @DeleteForDJVM
        @JvmStatic
        @JvmOverloads
        @Suppress("LongParameterList")
        fun rpc(
            actor: Actor,
            trace: Trace = Trace.newInstance(),
            externalTrace: Trace? = null,
            impersonatedActor: Actor? = null,
            arguments: List<Any?> = emptyList(),
            serializedTelemetry: SerializedTelemetry? = null
        ): InvocationContext = newInstance(InvocationOrigin.RPC(actor), trace, actor, externalTrace, impersonatedActor, arguments, serializedTelemetry = serializedTelemetry)

        /**
         * Creates an [InvocationContext] with [InvocationOrigin.Peer] origin.
         */
        @DeleteForDJVM
        @JvmStatic
        fun peer(party: CordaX500Name, trace: Trace = Trace.newInstance(), externalTrace: Trace? = null, impersonatedActor: Actor? = null): InvocationContext = newInstance(InvocationOrigin.Peer(party), trace, null, externalTrace, impersonatedActor)

        /**
         * Creates an [InvocationContext] with [InvocationOrigin.Service] origin.
         */
        @DeleteForDJVM
        @JvmStatic
        fun service(serviceClassName: String, owningLegalIdentity: CordaX500Name, trace: Trace = Trace.newInstance(), externalTrace: Trace? = null): InvocationContext = newInstance(InvocationOrigin.Service(serviceClassName, owningLegalIdentity), trace, null, externalTrace)

        /**
         * Creates an [InvocationContext] with [InvocationOrigin.Scheduled] origin.
         */
        @DeleteForDJVM
        @JvmStatic
        fun scheduled(scheduledState: ScheduledStateRef, trace: Trace = Trace.newInstance(), externalTrace: Trace? = null): InvocationContext = newInstance(InvocationOrigin.Scheduled(scheduledState), trace, null, externalTrace)

        /**
         * Creates an [InvocationContext] with [InvocationOrigin.Shell] origin.
         */
        @DeleteForDJVM
        @JvmStatic
        fun shell(trace: Trace = Trace.newInstance(), externalTrace: Trace? = null): InvocationContext = InvocationContext(InvocationOrigin.Shell, trace, null, externalTrace)
    }

    /**
     * Associated security principal.
     */
    fun principal(): Principal = origin.principal()

    fun copy(
        origin: InvocationOrigin = this.origin,
        trace: Trace = this.trace,
        actor: Actor? = this.actor,
        externalTrace: Trace? = this.externalTrace,
        impersonatedActor: Actor? = this.impersonatedActor
    ): InvocationContext {
        return copy(
            origin = origin,
            trace = trace,
            actor = actor,
            externalTrace = externalTrace,
            impersonatedActor = impersonatedActor,
            arguments = arguments,
            clientId = clientId
        )
    }

    @Suppress("LongParameterList")
    fun copy(
            origin: InvocationOrigin = this.origin,
            trace: Trace = this.trace,
            actor: Actor? = this.actor,
            externalTrace: Trace? = this.externalTrace,
            impersonatedActor: Actor? = this.impersonatedActor,
            arguments: List<Any?>? = this.arguments,
            clientId: String? = this.clientId
    ): InvocationContext {
        return copy(
                origin = origin,
                trace = trace,
                actor = actor,
                externalTrace = externalTrace,
                impersonatedActor = impersonatedActor,
                arguments = arguments,
                clientId = clientId,
                serializedTelemetry = serializedTelemetry
        )
    }
}

/**
 * Models an initiator in Corda, can be a user, a service, etc.
 */
@KeepForDJVM
@CordaSerializable
data class Actor(val id: Id, val serviceId: AuthServiceId, val owningLegalIdentity: CordaX500Name) {

    companion object {
        @JvmStatic
        fun service(serviceClassName: String, owningLegalIdentity: CordaX500Name): Actor = Actor(Id(serviceClassName), AuthServiceId("SERVICE"), owningLegalIdentity)
    }

    /**
     * Actor id.
     */
    @KeepForDJVM
    @CordaSerializable
    data class Id(val value: String)
}

/**
 * Represents the source of an action such as a flow start, an RPC, a shell command etc.
 */
@DeleteForDJVM
@CordaSerializable
sealed class InvocationOrigin {
    /**
     * Returns the [Principal] for a given [Actor].
     */
    abstract fun principal(): Principal

    /**
     * Origin was an RPC call.
     */
    // Field `actor` needs to stay public for AMQP / JSON serialization to work.
    data class RPC(val actor: Actor) : InvocationOrigin() {
        override fun principal() = Principal { actor.id.value }
    }

    /**
     * Origin was a message sent by a [Peer].
     */
    data class Peer(val party: CordaX500Name) : InvocationOrigin() {
        override fun principal() = Principal { party.toString() }
    }

    /**
     * Origin was a Corda Service.
     */
    data class Service(val serviceClassName: String, val owningLegalIdentity: CordaX500Name) : InvocationOrigin() {
        override fun principal() = Principal { serviceClassName }
    }

    /**
     * Origin was a scheduled activity.
     */
    data class Scheduled(val scheduledState: ScheduledStateRef) : InvocationOrigin() {
        override fun principal() = Principal { "Scheduler" }
    }

    // TODO When proper ssh access enabled, add username/use RPC?
    /**
     * Origin was the Shell.
     */
    object Shell : InvocationOrigin() {
        override fun principal() = Principal { "Shell User" }
    }
}

/**
 * Authentication / Authorisation Service ID.
 */
@KeepForDJVM
@CordaSerializable
data class AuthServiceId(val value: String)