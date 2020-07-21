package net.corda.node.services.config

import net.corda.core.internal.div
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

fun NodeHandle.logFile(): File = (baseDirectory / "logs").toFile().walk().filter { it.name.startsWith("node-") && it.extension == "log" }.single()

class NodeConfigParsingTests {
    @Test(timeout = 300_000)
    fun `bad keys are ignored and warned for`() {
        val portAllocator = incrementalPortAllocation()
        driver(DriverParameters(
                environmentVariables = mapOf(
                        "corda_bad_key" to "2077"),
                startNodesInProcess = false,
                portAllocation = portAllocator,
                notarySpecs = emptyList())) {

            val hasWarning = startNode()
                    .getOrThrow()
                    .logFile()
                    .readLines()
                    .any {
                        it.contains("(property or environment variable) cannot be mapped to an existing Corda")
                    }
            assertTrue(hasWarning)
        }
    }
}
