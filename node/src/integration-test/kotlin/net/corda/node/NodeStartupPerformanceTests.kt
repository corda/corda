package net.corda.node

import com.google.common.base.Stopwatch
import net.corda.node.logging.logFile
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.Assert
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

class NodeEnvironmentVariablesConversionTests {
    @Test
    fun `underscore variable is converted and utilized`() {
        val portAllocator = incrementalPortAllocation()
        val sshPort = portAllocator.nextPort()

        driver(DriverParameters(
                systemProperties = mapOf("corda_sshd_port" to sshPort.toString()),
                startNodesInProcess = false,
                portAllocation = portAllocator)) {
            val hasSsh = startNode().get()
                    .logFile()
                    .readLines()
                    .filter { it.contains("SSH server listening on port") }
                    .any { it.contains(sshPort.toString()) }
            assert(hasSsh)
        }
    }
}
