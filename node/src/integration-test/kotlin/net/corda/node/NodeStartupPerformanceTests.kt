package net.corda.node

import com.google.common.base.Stopwatch
import net.corda.testing.driver.driver
import org.junit.Ignore
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

@Ignore("Only use locally")
class NodeStartupPerformanceTests {

    // Measure the startup time of nodes. Note that this includes an RPC roundtrip, which causes e.g. Kryo initialisation.
    @Test
    fun `single node startup time`() {
        driver {
            val times = ArrayList<Long>()
            for (i in 1..10) {
                val time = Stopwatch.createStarted().apply {
                    startNode().get()
                }.stop().elapsed(TimeUnit.MICROSECONDS)
                times.add(time)
            }
            println(times.map { it / 1_000_000.0 })
        }
    }
}
