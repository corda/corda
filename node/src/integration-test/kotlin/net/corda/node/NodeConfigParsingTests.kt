package net.corda.node

import net.corda.node.logging.logFile
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.Test

class NodeConfigParsingTests {

    @Test
    fun `config is overriden by underscore variable`() {
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

    @Test
    fun `config is overriden by case insensitive underscore variable`() {
        val portAllocator = incrementalPortAllocation()
        val sshPort = portAllocator.nextPort()

        driver(DriverParameters(
                systemProperties = mapOf("cORDa_sShD_pOrt" to sshPort.toString()),
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

    @Test
    fun `config is overriden by case insensitive dot variable`() {
        val portAllocator = incrementalPortAllocation()
        val sshPort = portAllocator.nextPort()

        driver(DriverParameters(
                systemProperties = mapOf("cOrda.sShD.pOrt" to sshPort.toString()),
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
