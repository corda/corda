package net.corda.testing.driver

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.readLines
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.NodeStartup
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.http.HttpApi
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.addressMustBeBound
import net.corda.testing.node.internal.addressMustNotBeBound
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.*
import org.json.simple.JSONObject
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ScheduledExecutorService
import kotlin.streams.toList
import kotlin.test.assertEquals

class DriverTests {
    private companion object {
        val DUMMY_REGULATOR_NAME = CordaX500Name("Regulator A", "Paris", "FR")
        val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        fun nodeMustBeUp(handleFuture: CordaFuture<out NodeHandle>) = handleFuture.getOrThrow().apply {
            val hostAndPort = nodeInfo.addresses.single()
            // Check that the port is bound
            addressMustBeBound(executorService, hostAndPort, (this as? OutOfProcess)?.process)
        }

        fun nodeMustBeDown(handle: NodeHandle) {
            val hostAndPort = handle.nodeInfo.addresses.single()
            // Check that the port is bound
            addressMustNotBeBound(executorService, hostAndPort)
        }
    }

    @Test
    fun `simple node startup and shutdown`() {
        val handle = driver(DriverParameters(notarySpecs = emptyList())) {
            val node = startNode(providedName = DUMMY_REGULATOR_NAME)
            nodeMustBeUp(node)
        }
        nodeMustBeDown(handle)
    }

    @Test
    fun `starting with default notary`() {
        driver {
            // Make sure the default is a single-node notary
            val notary = defaultNotaryNode.getOrThrow()
            val notaryIdentities = notary.nodeInfo.legalIdentitiesAndCerts
            // Make sure the notary node has only one identity
            assertThat(notaryIdentities).hasSize(1)
            val identity = notaryIdentities[0]
            // Make sure this identity is a legal identity, like it is for normal nodes.
            assertThat(CertRole.extract(identity.certificate)).isEqualTo(CertRole.LEGAL_IDENTITY)
            // And make sure this identity is published as the notary identity (via the network parameters)
            assertThat(notary.rpc.notaryIdentities()).containsOnly(identity.party)
        }
    }

    @Test
    fun `default notary is visible when the startNode future completes`() {
        // Based on local testing, running this 3 times gives us a high confidence that we'll spot if the feature is not working
        repeat(3) {
            driver(DriverParameters(startNodesInProcess = true)) {
                val bob = startNode(providedName = BOB_NAME).getOrThrow()
                assertThat(bob.rpc.networkMapSnapshot().flatMap { it.legalIdentities }).contains(defaultNotaryIdentity)
            }
        }
    }

    @Test
    fun `debug mode enables debug logging level`() {
        // Make sure we're using the log4j2 config which writes to the log file
        val logConfigFile = projectRootDir / "config" / "dev" / "log4j2.xml"
        assertThat(logConfigFile).isRegularFile()
        driver(DriverParameters(
                isDebug = true,
                notarySpecs = emptyList(),
                systemProperties = mapOf("log4j.configurationFile" to logConfigFile.toString())
        )) {
            val baseDirectory = startNode(providedName = DUMMY_BANK_A_NAME).getOrThrow().baseDirectory
            val logFile = (baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list { it.sorted().findFirst().get() }
            val debugLinesPresent = logFile.readLines { lines -> lines.anyMatch { line -> line.startsWith("[DEBUG]") } }
            assertThat(debugLinesPresent).isTrue()
        }
    }

    @Test
    fun `monitoring mode enables jolokia exporting of JMX metrics via HTTP JSON`() {
        driver(DriverParameters(startNodesInProcess = false, notarySpecs = emptyList())) {
            // start another node so we gain access to node JMX metrics
            val webAddress = NetworkHostAndPort("localhost", 7006)
            startNode(providedName = DUMMY_REGULATOR_NAME,
                      customOverrides = mapOf("jmxMonitoringHttpPort" to webAddress.port)).getOrThrow()
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
            val baseDirectory = defaultNotaryNode.getOrThrow().baseDirectory
            assertThat(baseDirectory / "process-id").exists()
        }

        val baseDirectory = internalDriver(notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME))) {
            baseDirectory(DUMMY_NOTARY_NAME)
        }
        assertThat(baseDirectory / "process-id").doesNotExist()
    }

    @Test
    fun `driver rejects multiple nodes with the same name`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            assertThatIllegalArgumentException().isThrownBy {
                listOf(
                        newNode(DUMMY_BANK_A_NAME)(),
                        newNode(DUMMY_BANK_B_NAME)(),
                        newNode(DUMMY_BANK_A_NAME)()
                ).transpose().getOrThrow()
            }
        }
    }

    @Test
    fun `driver rejects multiple nodes with the same name parallel`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodes = listOf(newNode(DUMMY_BANK_A_NAME), newNode(DUMMY_BANK_B_NAME), newNode(DUMMY_BANK_A_NAME))
            assertThatIllegalArgumentException().isThrownBy {
                nodes.parallelStream().map { it.invoke() }.toList().transpose().getOrThrow()
            }
        }
    }

    @Test
    fun `driver rejects multiple nodes with the same organisation name`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            listOf(
                    newNode(CordaX500Name(commonName = "Notary", organisation = "R3CEV", locality = "New York", country = "US"))(),
                    newNode(DUMMY_BANK_A_NAME)()
            ).transpose().getOrThrow()
            assertThatIllegalArgumentException().isThrownBy {
                newNode(CordaX500Name(commonName = "Regulator", organisation = "R3CEV", locality = "New York", country = "US"))().getOrThrow()
            }
        }
    }

    @Test
    fun `driver allows reusing names of nodes that have been stopped`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodeA = newNode(DUMMY_BANK_A_NAME)().getOrThrow()
            nodeA.stop()
            assertThatCode { newNode(DUMMY_BANK_A_NAME)().getOrThrow() }.doesNotThrowAnyException()
        }
    }


    @Test
    fun `driver waits for in-process nodes to finish`() {
        fun NodeHandle.stopQuietly() = try {
            stop()
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        val handlesFuture = openFuture<List<NodeHandle>>()
        val driverExit = CountDownLatch(1)
        val testFuture = ForkJoinPool.commonPool().fork {
            val handles = LinkedList(handlesFuture.getOrThrow())
            val last = handles.removeLast()
            handles.forEach { it.stopQuietly() }
            assertEquals(1, driverExit.count)
            last.stopQuietly()
        }
        driver(DriverParameters(startNodesInProcess = true, waitForAllNodesToFinish = true)) {
            val nodeA = newNode(DUMMY_BANK_A_NAME)().getOrThrow()
            handlesFuture.set(listOf(nodeA) + notaryHandles.map { it.nodeHandles.getOrThrow() }.flatten())
        }
        driverExit.countDown()
        testFuture.getOrThrow()
    }

    private fun DriverDSL.newNode(name: CordaX500Name) = { startNode(NodeParameters(providedName = name)) }
}