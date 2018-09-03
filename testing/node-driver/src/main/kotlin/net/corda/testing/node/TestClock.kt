package net.corda.testing.node

import net.corda.core.internal.until
import net.corda.node.MutableClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.annotation.concurrent.ThreadSafe

/** A [Clock] that can have the time advanced for use in testing. */
@ThreadSafe
class TestClock(delegateClock: Clock) : MutableClock(delegateClock) {
    /** Advance this [Clock] by the specified [Duration] for testing purposes. */
    @Synchronized
    fun advanceBy(duration: Duration) {
        delegateClock = offset(delegateClock, duration)
        notifyMutationObservers()
    }

    /**
     * Move this [Clock] to the specified [Instant] for testing purposes.
     * This will only be approximate due to the time ticking away, but will be some time shortly after the requested [Instant].
     */
    @Synchronized
    fun setTo(newInstant: Instant) = advanceBy(instant() until newInstant)
}
