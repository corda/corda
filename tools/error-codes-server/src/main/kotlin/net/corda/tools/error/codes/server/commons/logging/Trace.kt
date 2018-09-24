package net.corda.tools.error.codes.server.commons.logging

import net.corda.tools.error.codes.server.commons.domain.identity.AbstractId
import net.corda.tools.error.codes.server.commons.domain.identity.UuidGenerator
import java.time.Instant

data class Trace(val invocationId: InvocationId, val sessionId: SessionId) {

    companion object {

        @JvmStatic
        fun newInstance(invocationId: InvocationId = InvocationId.newInstance(), sessionId: SessionId = SessionId(invocationId.value, invocationId.timestamp)) = Trace(invocationId, sessionId)
    }

    class InvocationId(value: String, timestamp: Instant) : AbstractId<String>(value, TYPE, timestamp) {

        companion object {
            private const val TYPE = "Invocation"

            @JvmStatic
            fun newInstance(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) = InvocationId(value, timestamp)
        }
    }

    class SessionId(value: String, timestamp: Instant) : AbstractId<String>(value, TYPE, timestamp) {

        companion object {
            private const val TYPE = "Session"

            @JvmStatic
            fun newInstance(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) = SessionId(value, timestamp)
        }
    }
}