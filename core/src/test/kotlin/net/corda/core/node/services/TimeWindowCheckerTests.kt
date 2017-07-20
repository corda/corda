package net.corda.core.node.services

import net.corda.core.contracts.TimeWindow
import net.corda.core.utilities.seconds
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeWindowCheckerTests {
    val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
    val timeWindowChecker = TimeWindowChecker(clock)

    @Test
    fun `should return true for valid time-window`() {
        val now = clock.instant()
        val timeWindowBetween = TimeWindow.between(now - 10.seconds, now + 10.seconds)
        val timeWindowFromOnly = TimeWindow.fromOnly(now - 10.seconds)
        val timeWindowUntilOnly = TimeWindow.untilOnly(now + 10.seconds)
        assertTrue { timeWindowChecker.isValid(timeWindowBetween) }
        assertTrue { timeWindowChecker.isValid(timeWindowFromOnly) }
        assertTrue { timeWindowChecker.isValid(timeWindowUntilOnly) }
    }

    @Test
    fun `should return false for invalid time-window`() {
        val now = clock.instant()
        val timeWindowBetweenPast = TimeWindow.between(now - 10.seconds, now - 2.seconds)
        val timeWindowBetweenFuture = TimeWindow.between(now + 2.seconds, now + 10.seconds)
        val timeWindowFromOnlyFuture = TimeWindow.fromOnly(now + 10.seconds)
        val timeWindowUntilOnlyPast = TimeWindow.untilOnly(now - 10.seconds)
        assertFalse { timeWindowChecker.isValid(timeWindowBetweenPast) }
        assertFalse { timeWindowChecker.isValid(timeWindowBetweenFuture) }
        assertFalse { timeWindowChecker.isValid(timeWindowFromOnlyFuture) }
        assertFalse { timeWindowChecker.isValid(timeWindowUntilOnlyPast) }
    }
}
