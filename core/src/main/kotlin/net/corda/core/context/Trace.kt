package net.corda.core.context

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.Id
import net.corda.core.utilities.UuidGenerator
import java.time.Instant

/**
 * Contextual tracing information, including invocation and session id.
 */
@CordaSerializable
data class Trace(val invocationId: InvocationId, val sessionId: SessionId) {

    companion object {

        /**
         * Creates a trace using a [InvocationId.newInstance] with default arguments and a [SessionId] matching the value and timestamp from the invocation id..
         */
        @JvmStatic
        fun newInstance(invocationId: InvocationId = InvocationId.newInstance(), sessionId: SessionId = SessionId(invocationId.value, invocationId.timestamp)) = Trace(invocationId, sessionId)
    }

    /**
     * Represents id and timestamp of an invocation.
     */
    @CordaSerializable
    class InvocationId(value: String, timestamp: Instant) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private const val TYPE = "Invocation"

            /**
             * Creates an invocation id using a [java.util.UUID] as value and [Instant.now] as timestamp.
             */
            @JvmStatic
            fun newInstance(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) = InvocationId(value, timestamp)
        }
    }

    /**
     * Represents id and timestamp of a session.
     */
    @CordaSerializable
    class SessionId(value: String, timestamp: Instant) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private const val TYPE = "Session"

            /**
             * Creates a session id using a [java.util.UUID] as value and [Instant.now] as timestamp.
             */
            @JvmStatic
            fun newInstance(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) = SessionId(value, timestamp)
        }
    }
}