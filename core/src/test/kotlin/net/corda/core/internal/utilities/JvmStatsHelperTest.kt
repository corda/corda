package net.corda.core.internal.utilities

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import org.junit.Test
import kotlin.test.assertNotNull

class JvmStatsHelperTest {
    @Test
    fun test() {
        val dumper = JvmStatsHelper()
        val stats = dumper.stats
        assertNotNull(stats)
        assertThat(stats, containsSubstring("GC"))
    }
}