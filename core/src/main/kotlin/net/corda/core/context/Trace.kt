package net.corda.core.context

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.Id
import net.corda.core.utilities.UuidGenerator
import java.time.Instant

/**
 * Contextual tracing information, including invocation and session id.
 */
@CordaSerializable
data class Trace(val invocationId: InvocationId = InvocationId(), val sessionId: SessionId = SessionId(invocationId.value, invocationId.timestamp)) {

    /**
     * Represents id and timestamp of an invocation.
     */
    @CordaSerializable
    class InvocationId(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private val TYPE = "Invocation"
        }
    }

    /**
     * Represents id and timestamp of a session.
     */
    @CordaSerializable
    class SessionId(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private val TYPE = "Session"
        }
    }
}