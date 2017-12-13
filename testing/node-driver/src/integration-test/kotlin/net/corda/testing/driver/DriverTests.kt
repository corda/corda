package net.corda.testing.driver

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.readLines
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.NodeStartup
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.getX509Certificate
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import net.corda.testing.*
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.node.internal.addressMustBeBound
import net.corda.testing.node.internal.addressMustNotBeBound
import net.corda.testing.node.internal.internalDriver
import net.corda.testing.DUMMY_BANK_A_NAME
import net.corda.testing.DUMMY_NOTARY_NAME
import net.corda.testing.http.HttpApi
import net.corda.testing.node.NotarySpec
import org.assertj.core.api.Assertions.assertThat
import org.json.simple.JSONObject
import org.junit.Rule
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class DriverTests : IntegrationTest() {
    companion object {
        val DUMMY_REGULATOR_NAME = CordaX500Name("Regulator A", "Paris", "FR")
        val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        fun nodeMustBeUp(handleFuture: CordaFuture<out NodeHandle>) = handleFuture.getOrThrow().apply {
            val hostAndPort = nodeInfo.addresses.first()
            // Check that the port is bound
            addressMustBeBound(executorService, hostAndPort, (this as? NodeHandle.OutOfProcess)?.process)
        }

        fun nodeMustBeDown(handle: NodeHandle) {
            val hostAndPort = handle.nodeInfo.addresses.first()
            // Check that the port is bound
            addressMustNotBeBound(executorService, hostAndPort)
        }

        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(DUMMY_BANK_A, DUMMY_NOTARY, DUMMY_REGULATOR)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    @Test
    fun `simple node startup and shutdown`() {
        val handle = driver {
            val regulator = startNode(providedName = DUMMY_REGULATOR_NAME)
            nodeMustBeUp(regulator)
        }
        nodeMustBeDown(handle)
    }

    @Test
    fun `random free port allocation`() {
        val nodeHandle = driver(portAllocation = PortAllocation.RandomFree) {
            val nodeInfo = startNode(providedName = DUMMY_BANK_A_NAME)
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
            val baseDirectory = startNode(providedName = DUMMY_BANK_A_NAME).getOrThrow().configuration.baseDirectory
            val logFile = (baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list { it.sorted().findFirst().get() }
            val debugLinesPresent = logFile.readLines { lines -> lines.anyMatch { line -> line.startsWith("[DEBUG]") } }
            assertThat(debugLinesPresent).isTrue()
        }
    }

    @Test
    fun `monitoring mode enables jolokia exporting of JMX metrics via HTTP JSON`() {
        driver(jmxPolicy = JmxPolicy(true)) {
            // start another node so we gain access to node JMX metrics
            startNode(providedName = DUMMY_REGULATOR_NAME).getOrThrow()
            val webAddress = NetworkHostAndPort("localhost", 7006)
            // request access to some JMX metrics via Jolokia HTTP/JSON
            val api = HttpApi.fromHostAndPort(webAddress, "/jolokia/")
            val versionAsJson = api.getJson<JSONObject>("/jolokia/version/")
            assertThat(versionAsJson.getValue("status")).isEqualTo(200)
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

        val baseDirectory = internalDriver(notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME))) {
            baseDirectory(DUMMY_NOTARY_NAME)
        }
        assertThat(baseDirectory / "process-id").doesNotExist()
    }
}
