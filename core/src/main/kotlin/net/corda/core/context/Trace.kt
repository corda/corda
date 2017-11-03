package net.corda.core.context

import net.corda.core.utilities.Id
import net.corda.core.utilities.UuidGenerator
import java.time.Instant

// TODO sollecitom docs
data class Trace(val invocationId: InvocationId = InvocationId(), val sessionId: SessionId = SessionId(invocationId.value, invocationId.timestamp), val parent: Trace? = null) {

    class InvocationId(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private val TYPE = "Invocation"
        }
    }

    class SessionId(value: String = UuidGenerator.next().toString(), timestamp: Instant = Instant.now()) : Id<String>(value, TYPE, timestamp) {

        companion object {
            private val TYPE = "Session"
        }
    }

    val flattened: List<Trace> by lazy {

        val result = mutableListOf<Trace>()
        var current: Trace? = this

        do {
            result += current!!.copy()
            current = current!!.parent
        } while (current != null)

        result
    }

    // TODO sollecitom perhaps add IntervalTreeClock

    // TODO sollecitom perhaps add latency deriving extensions to this
}