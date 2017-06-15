package net.corda.core.utilities

import java.time.Duration
import java.time.Instant

/**
 * A class representing a window in time from a particular instant, lasting a specified duration.
 */
data class TimeFrame(val start: Instant, val duration: Duration) {
    val end: Instant
        get() = start + duration
}
