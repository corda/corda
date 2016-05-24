package com.r3corda.core.testutils

import com.r3corda.core.utilities.MutableClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.annotation.concurrent.ThreadSafe


/**
 * A [Clock] that can have the time advanced for use in testing
 */
@ThreadSafe
class TestClock(private var delegateClock: Clock = Clock.systemUTC()) : MutableClock() {

    @Synchronized fun advanceBy(duration: Duration): Boolean {
        if (!duration.isNegative) {
            // It's ok to increment
            delegateClock = offset(delegateClock, duration)
            notifyMutationObservers()
            return true
        }
        return false
    }

    @Synchronized override fun instant(): Instant {
        return delegateClock.instant()
    }

    @Synchronized override fun withZone(zone: ZoneId): Clock {
        return TestClock(delegateClock.withZone(zone))
    }

    @Synchronized override fun getZone(): ZoneId {
        return delegateClock.zone
    }
}
