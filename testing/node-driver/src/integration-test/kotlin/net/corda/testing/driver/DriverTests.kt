package net.corda.testing.driver

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.readLines
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.NodeStartup
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.DUMMY_REGULATOR
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.internal.addressMustBeBound
import net.corda.testing.internal.addressMustNotBeBound
import net.corda.testing.node.NotarySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class DriverTests {
    companion object {
        private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        private fun nodeMustBeUp(handleFuture: CordaFuture<out NodeHandle>) = handleFuture.getOrThrow().apply {
            val hostAndPort = nodeInfo.addresses.first()
            // Check that the port is bound
            addressMustBeBound(executorService, hostAndPort, (this as? NodeHandle.OutOfProcess)?.process)
        }

        private fun nodeMustBeDown(handle: NodeHandle) {
            val hostAndPort = handle.nodeInfo.addresses.first()
            // Check that the port is bound
            addressMustNotBeBound(executorService, hostAndPort)
        }
    }

    @Test
    fun `simple node startup and shutdown`() {
        val handle = driver {
            val regulator = startNode(providedName = DUMMY_REGULATOR.name)
            nodeMustBeUp(regulator)
        }
        nodeMustBeDown(handle)
    }

    @Test
    fun `random free port allocation`() {
        val nodeHandle = driver(portAllocation = PortAllocation.RandomFree) {
            val nodeInfo = startNode(providedName = DUMMY_BANK_A.name)
            nodeMustBeUp(nodeInfo)
        }
        nodeMustBeDown(nodeHandle)
    }

    @Test
    fun `debug mode enables debug logging level`() {
        // Make sure we're using the log4j2 config which writes to the log file
        val logConfigFile = projectRootDir / "config" / "dev" / "log4j2.xml"
        assertThat(logConfigFile).isRegularFile()
        driver(isDebug = true, systemProperties = mapOf("log4j.configurationFile" to logConfigFile.toString())) {
            val baseDirectory = startNode(providedName = DUMMY_BANK_A.name).getOrThrow().configuration.baseDirectory
            val logFile = (baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list { it.sorted().findFirst().get() }
            val debugLinesPresent = logFile.readLines { lines -> lines.anyMatch { line -> line.startsWith("[DEBUG]") } }
            assertThat(debugLinesPresent).isTrue()
        }
    }

    @Test
    fun `started node, which is not waited for in the driver, is shutdown when the driver exits`() {
        // First check that the process-id file is created by the node on startup, so that we can be sure our check that
        // it's deleted on shutdown isn't a false-positive.
        driver {
            val baseDirectory = defaultNotaryNode.getOrThrow().configuration.baseDirectory
            assertThat(baseDirectory / "process-id").exists()
        }

        val baseDirectory = driver(notarySpecs = listOf(NotarySpec(DUMMY_NOTARY.name))) {
            baseDirectory(DUMMY_NOTARY.name)
        }
        assertThat(baseDirectory / "process-id").doesNotExist()
    }
}
