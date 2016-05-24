package com.r3corda.demos

import com.r3corda.node.utilities.MutableClock
import java.time.*
import javax.annotation.concurrent.ThreadSafe

/**
 * A [Clock] that can have the date advanced for use in demos
 */
@ThreadSafe
class DemoClock(private var delegateClock: Clock = Clock.systemUTC()) : MutableClock() {

    @Synchronized fun updateDate(date: LocalDate): Boolean {
        val currentDate = LocalDate.now(this)
        if (currentDate.isBefore(date)) {
            // It's ok to increment
            delegateClock = Clock.offset(delegateClock, Duration.between(currentDate.atStartOfDay(), date.atStartOfDay()))
            notifyMutationObservers()
            return true
        }
        return false
    }

    @Synchronized override fun instant(): Instant {
        return delegateClock.instant()
    }

    @Synchronized override fun withZone(zone: ZoneId): Clock {
        return DemoClock(delegateClock.withZone(zone))
    }

    @Synchronized override fun getZone(): ZoneId {
        return delegateClock.zone
    }

}