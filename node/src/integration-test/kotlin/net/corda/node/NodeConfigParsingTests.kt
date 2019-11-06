package net.corda.node

import net.corda.core.utilities.getOrThrow
import net.corda.node.logging.logFile
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class NodeConfigParsingTests {

    @Test
    fun `config is overriden by underscore variable`() {
        val portAllocator = incrementalPortAllocation()
        val sshPort = portAllocator.nextPort()

        driver(DriverParameters(
                environmentVariables = mapOf("corda_sshd_port" to sshPort.toString()),
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
                environmentVariables = mapOf("corda_sshd_port" to sshPort.toString()),
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
                environmentVariables = mapOf("cOrda.sshd.port" to sshPort.toString(),
                                             "coRda.devMode" to true.toString()),
                startNodesInProcess = false,
                portAllocation = portAllocator)) {
            val hasSsh = startNode(NodeParameters()).get()
                    .logFile()
                    .readLines()
                    .filter { it.contains("SSH server listening on port") }
                    .any { it.contains(sshPort.toString()) }
            assert(hasSsh)
        }
    }

    @Test
    fun `shadowing is forbidden`() {
        val portAllocator = incrementalPortAllocation()
        val sshPort = portAllocator.nextPort()

        driver(DriverParameters(
                environmentVariables = mapOf(
                        "cOrda_sshd_port" to sshPort.toString(),
                        "cORda.sshd.port" to sshPort.toString()),
                startNodesInProcess = false,
                portAllocation = portAllocator,
                notarySpecs = emptyList())) {

            assertThatThrownBy {
                startNode().getOrThrow()
            }
        }
    }

    @Test
    fun `bad keys are ignored and warned for`() {
        val portAllocator = incrementalPortAllocation()
        driver(DriverParameters(
                environmentVariables = mapOf(
                        "cOrda_bad_key" to "2077"),
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
                assert(hasWarning)
        }
    }
}
