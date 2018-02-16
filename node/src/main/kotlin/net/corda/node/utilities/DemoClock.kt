package net.corda.node.utilities

import net.corda.core.internal.until
import net.corda.node.MutableClock
import java.time.Clock
import java.time.LocalDate
import javax.annotation.concurrent.ThreadSafe

/** A [Clock] that can have the date advanced for use in demos. */
@ThreadSafe
class DemoClock(delegateClock: Clock) : MutableClock(delegateClock) {
    @Synchronized
    fun updateDate(date: LocalDate): Boolean {
        val currentDate = LocalDate.now(this)
        if (currentDate.isBefore(date)) {
            // It's ok to increment
            delegateClock = Clock.offset(delegateClock, currentDate.atStartOfDay() until date.atStartOfDay())
            notifyMutationObservers()
            return true
        }
        return false
    }
}
