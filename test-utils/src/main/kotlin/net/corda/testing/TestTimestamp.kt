package net.corda.testing

import net.corda.testing.TestTimestamp.Companion.timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * [timestamp] holds a formatted (UTC) timestamp that's set the first time it is queried. This is used to
 * provide a uniform timestamp for tests.
 */
class TestTimestamp {
    companion object {
        val timestamp: String = {
            val tz = TimeZone.getTimeZone("UTC")
            val df = SimpleDateFormat("yyyyMMddHHmmss")
            df.timeZone = tz
            df.format(Date())
        }()
    }
}
