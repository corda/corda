package net.corda.core.context

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.Id
import net.corda.core.utilities.UuidGenerator
import java.time.Instant

// TODO sollecitom docs
@CordaSerializable
data class Trace(val invocationId: InvocationId = InvocationId(), val sessionId: SessionId = SessionId(invocationId.value, invocationId.timestamp)) {

    @CordaSerializable
    class InvocationId(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private val TYPE = "Invocation"
        }
    }

    @CordaSerializable
    class SessionId(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private val TYPE = "Session"
        }
    }

    // TODO sollecitom perhaps add IntervalTreeClock

    // TODO sollecitom perhaps add latency deriving extensions to this
}