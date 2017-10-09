package net.corda.testing.node

import net.corda.core.internal.until
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.serialization.SingletonSerializationToken.Companion.singletonSerializationToken
import net.corda.node.internal.MutableClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.annotation.concurrent.ThreadSafe


/**
 * A [Clock] that can have the time advanced for use in testing.
 */
@ThreadSafe
class TestClock(private var delegateClock: Clock = Clock.systemUTC()) : MutableClock(), SerializeAsToken {

    private val token = singletonSerializationToken(javaClass)

    override fun toToken(context: SerializeAsTokenContext) = token.registerWithContext(context, this)

    /**
     * Advance this [Clock] by the specified [Duration] for testing purposes.
     */
    @Synchronized
    fun advanceBy(duration: Duration) {
        delegateClock = offset(delegateClock, duration)
        notifyMutationObservers()
    }

    /**
     * Move this [Clock] to the specified [Instant] for testing purposes.
     *
     * This will only be approximate due to the time ticking away, but will be some time shortly after the requested [Instant].
     */
    @Synchronized
    fun setTo(newInstant: Instant) = advanceBy(instant() until newInstant)

    @Synchronized override fun instant(): Instant {
        return delegateClock.instant()
    }

    @Deprecated("Do not use this. Instead seek to use ZonedDateTime methods.", level = DeprecationLevel.ERROR)
    override fun withZone(zone: ZoneId): Clock {
        throw UnsupportedOperationException("Tokenized clock does not support withZone()")
    }

    @Synchronized override fun getZone(): ZoneId {
        return delegateClock.zone
    }
}

/**
 * A helper method to set several [TestClock]s to approximately the same time.  The clocks may drift by the time it
 * takes for each [TestClock] to have it's time set and any observers to execute.
 */
fun Iterable<TestClock>.setTo(instant: Instant) = this.forEach { it.setTo(instant) }
