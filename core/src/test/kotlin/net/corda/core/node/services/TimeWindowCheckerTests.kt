package net.corda.core.node.services

import net.corda.core.contracts.TimeWindow
import net.corda.core.seconds
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeWindowCheckerTests {
    val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    val timeWindowChecker = TimeWindowChecker(clock, tolerance = 30.seconds)

    @Test
    fun `should return true for valid time-window`() {
        val now = clock.instant()
        val timeWindowPast = TimeWindow.between(now - 60.seconds, now - 29.seconds)
        val timeWindowFuture = TimeWindow.between(now + 29.seconds, now + 60.seconds)
        assertTrue { timeWindowChecker.isValid(timeWindowPast) }
        assertTrue { timeWindowChecker.isValid(timeWindowFuture) }
    }

    @Test
    fun `should return false for invalid time-window`() {
        val now = clock.instant()
        val timeWindowPast = TimeWindow.between(now - 60.seconds, now - 31.seconds)
        val timeWindowFuture = TimeWindow.between(now + 31.seconds, now + 60.seconds)
        assertFalse { timeWindowChecker.isValid(timeWindowPast) }
        assertFalse { timeWindowChecker.isValid(timeWindowFuture) }
    }
}
