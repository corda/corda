package net.corda.core.node.services

import net.corda.core.contracts.TimeWindow
import net.corda.core.utilities.seconds
import net.corda.core.until
import java.time.Clock
import java.time.Duration

/**
 * Checks if the given time-window falls within the allowed tolerance interval.
 */
class TimeWindowChecker(val clock: Clock = Clock.systemUTC(),
                        val tolerance: Duration = 30.seconds) {
    fun isValid(timeWindow: TimeWindow): Boolean {
        val untilTime = timeWindow.untilTime
        val fromTime = timeWindow.fromTime

        val now = clock.instant()

        // We don't need to test for (fromTime == null && untilTime == null) or backwards bounds because the TimeWindow
        // constructor already checks that.
        if (untilTime != null && untilTime until now > tolerance) return false
        if (fromTime != null && now until fromTime > tolerance) return false
        return true
    }
}
