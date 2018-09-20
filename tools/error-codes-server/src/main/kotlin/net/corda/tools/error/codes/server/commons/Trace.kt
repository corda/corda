package net.corda.tools.error.codes.server.commons

import net.corda.tools.error.codes.server.commons.identity.Id
import net.corda.tools.error.codes.server.commons.identity.UuidGenerator
import java.time.Instant

data class Trace(val invocationId: InvocationId, val sessionId: SessionId) {

    companion object {

        @JvmStatic
        fun newInstance(invocationId: InvocationId = InvocationId.newInstance(), sessionId: SessionId = SessionId(invocationId.value, invocationId.timestamp)) = Trace(invocationId, sessionId)
    }

    class InvocationId(value: String, timestamp: Instant) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private const val TYPE = "Invocation"

            @JvmStatic
            fun newInstance(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) = InvocationId(value, timestamp)
        }
    }

    class SessionId(value: String, timestamp: Instant) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private const val TYPE = "Session"

            @JvmStatic
            fun newInstance(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) = SessionId(value, timestamp)
        }
    }
}