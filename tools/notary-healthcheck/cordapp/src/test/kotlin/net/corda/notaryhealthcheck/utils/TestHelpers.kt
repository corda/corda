package net.corda.notaryhealthcheck.utils

import net.corda.core.utilities.millis
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class TestHelpers {
    @Test
    fun testDurationPrinting() {
        assertEquals("00:00:00.012", Duration.from(12.millis).toHumanReadable())
        assertEquals("00:00:01.051", Duration.from(1051.millis).toHumanReadable())
        assertEquals("01:25:54.083", Duration.ofHours(1).plusMinutes(25).plusSeconds(54).plusMillis(83).toHumanReadable())
        assertEquals("5d 10:41:22.485", Duration.ofDays(5).plusHours(10).plusMinutes(41).plusSeconds(22).plusMillis(485).plusNanos(723743).toHumanReadable())

    }
}