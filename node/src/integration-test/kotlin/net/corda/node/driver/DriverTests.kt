package net.corda.node.driver

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.list
import net.corda.core.node.services.ServiceInfo
import net.corda.core.readLines
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_REGULATOR
import net.corda.node.LOGS_DIRECTORY_NAME
import net.corda.node.services.api.RegulatorService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.ArtemisMessagingComponent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class DriverTests {

    companion object {

        private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        private fun nodeMustBeUp(handleFuture: ListenableFuture<NodeHandle>) = handleFuture.getOrThrow().apply {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
            // Check that the port is bound
            addressMustBeBound(executorService, hostAndPort, process)
        }

        private fun nodeMustBeDown(handle: NodeHandle) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(handle.nodeInfo.address)
            // Check that the port is bound
            addressMustNotBeBound(executorService, hostAndPort)
        }

    }

    @Test
    fun `simple node startup and shutdown`() {
        val handles = driver {
            val notary = startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type)))
            val regulator = startNode(DUMMY_REGULATOR.name, setOf(ServiceInfo(RegulatorService.type)))
            listOf(nodeMustBeUp(notary), nodeMustBeUp(regulator))
        }
        handles.map { nodeMustBeDown(it) }
    }

    @Test
    fun `starting node with no services`() {
        val noService = driver {
            val noService = startNode(DUMMY_BANK_A.name)
            nodeMustBeUp(noService)
        }
        nodeMustBeDown(noService)
    }

    @Test
    fun `random free port allocation`() {
        val nodeHandle = driver(portAllocation = PortAllocation.RandomFree) {
            val nodeInfo = startNode(DUMMY_BANK_A.name)
            nodeMustBeUp(nodeInfo)
        }
        nodeMustBeDown(nodeHandle)
    }

    @Test
    fun `debug mode enables debug logging level`() {
        // Make sure we're using the log4j2 config which writes to the log file
        val logConfigFile = Paths.get("..", "config", "dev", "log4j2.xml").toAbsolutePath()
        assertThat(logConfigFile).isRegularFile()
        driver(isDebug = true, systemProperties = mapOf("log4j.configurationFile" to logConfigFile.toString())) {
            val baseDirectory = startNode(DUMMY_BANK_A.name).getOrThrow().configuration.baseDirectory
            val logFile = (baseDirectory / LOGS_DIRECTORY_NAME).list { it.sorted().findFirst().get() }
            val debugLinesPresent = logFile.readLines { lines -> lines.anyMatch { line -> line.startsWith("[DEBUG]") } }
            assertThat(debugLinesPresent).isTrue()
        }
    }

}
