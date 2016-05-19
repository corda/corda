package node.services.transactions

import core.contracts.TimestampCommand
import core.seconds
import core.until
import java.time.Clock
import java.time.Duration

/**
 * Checks if the given timestamp falls within the allowed tolerance interval
 */
class TimestampChecker(val clock: Clock = Clock.systemDefaultZone(),
                       val tolerance: Duration = 30.seconds) {
    fun isValid(timestampCommand: TimestampCommand): Boolean {
        val before = timestampCommand.before
        val after = timestampCommand.after

        val now = clock.instant()

        // We don't need to test for (before == null && after == null) or backwards bounds because the TimestampCommand
        // constructor already checks that.
        if (before != null && before until now > tolerance) return false
        if (after != null && now until after > tolerance) return false
        return true
    }
}