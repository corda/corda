package net.corda.testing.driver

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.concurrent.transpose
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.json.simple.JSONObject
import org.junit.Test
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ScheduledExecutorService
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries
import kotlin.io.path.useLines
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

    @Test(timeout=300_000)
	fun `simple node startup and shutdown`() {
        val handle = driver(DriverParameters(notarySpecs = emptyList())) {
            val node = startNode(providedName = DUMMY_REGULATOR_NAME)
            nodeMustBeUp(node)
        }
        nodeMustBeDown(handle)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `default notary is visible when the startNode future completes`() {
        // Based on local testing, running this 3 times gives us a high confidence that we'll spot if the feature is not working
        repeat(3) {
            driver(DriverParameters(startNodesInProcess = true)) {
                val bob = startNode(providedName = BOB_NAME).getOrThrow()
                assertThat(bob.rpc.networkMapSnapshot().flatMap { it.legalIdentities }).contains(defaultNotaryIdentity)
            }
        }
    }

    @Test(timeout=300_000)
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
            val logFile = (baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).useDirectoryEntries { it.single { a -> a.isRegularFile() && a.name.startsWith("node") } }
            val debugLinesPresent = logFile.useLines { lines -> lines.any { line -> line.startsWith("[DEBUG]") } }
            assertThat(debugLinesPresent).isTrue()
        }
    }

    @Test(timeout=300_000)
	fun `monitoring mode enables jolokia exporting of JMX metrics via HTTP JSON`() {
        driver(DriverParameters(jmxPolicy = JmxPolicy.defaultEnabled(), startNodesInProcess = false, notarySpecs = emptyList())) {
            val node = startNode(providedName = DUMMY_REGULATOR_NAME).getOrThrow()
            // request access to some JMX metrics via Jolokia HTTP/JSON
            val api = HttpApi.fromHostAndPort(node.jmxAddress!!, "/jolokia/")
            val versionAsJson = api.getJson<JSONObject>("/jolokia/version/")
            assertThat(versionAsJson.getValue("status")).isEqualTo(200)
        }
    }

    @Test(timeout=300_000)
	fun `started node, which is not waited for in the driver, is shutdown when the driver exits`() {
        // First check that the process-id file is created by the node on startup, so that we can be sure our check that
        // it's deleted on shutdown isn't a false-positive.
        val baseDirectory = driver(DriverParameters(notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, startInProcess = false)))) {
            val baseDirectory = defaultNotaryNode.getOrThrow().baseDirectory
            assertThat(baseDirectory / "process-id").exists()
            baseDirectory
        }

        // Should be able to start another node up in that directory
        assertThat(NodeStartup().isNodeRunningAt(baseDirectory)).isTrue()
    }

    @Test(timeout=300_000)
	fun `driver rejects multiple nodes with the same name parallel`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodes = listOf(newNode(DUMMY_BANK_A_NAME), newNode(DUMMY_BANK_B_NAME), newNode(DUMMY_BANK_A_NAME))
            assertThatIllegalArgumentException().isThrownBy {
                nodes.parallelStream().map { it.invoke() }.toList().transpose().getOrThrow()
            }
        }
    }

    /**
     * The uniqueness of nodes by the DSL is checked using the node organisation name and, if specified,
     * the organisation unit name.
     * All other X500 components are ignored in this regard.
     */
    @Test(timeout=300_000)
	fun `driver rejects multiple nodes with the same organisation name`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            newNode(CordaX500Name(commonName = "Notary", organisation = "R3CEV", locality = "New York", country = "US"))().getOrThrow()
            assertThatIllegalArgumentException().isThrownBy {
                newNode(CordaX500Name(commonName = "Regulator", organisation = "R3CEV", locality = "Newcastle", country = "GB"))().getOrThrow()
            }
        }
    }

    @Test(timeout=300_000)
    fun `driver allows multiple nodes with the same organisation name but different organisation unit name`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            newNode(CordaX500Name(commonName = "Notary", organisation = "R3CEV", organisationUnit = "Eric", locality = "New York", country = "US", state = null))().getOrThrow()
            assertThatCode {
                newNode(CordaX500Name(commonName = "Regulator", organisation = "R3CEV", organisationUnit = "Ernie", locality = "Newcastle", country = "GB", state = null))().getOrThrow()
            }.doesNotThrowAnyException()
        }
    }

    @Test(timeout=300_000)
    fun `driver rejects multiple nodes with the same organisation name and organisation unit name`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            newNode(CordaX500Name(commonName = "Notary", organisation = "R3CEV", organisationUnit = "Eric", locality = "New York", country = "US", state = null))().getOrThrow()
            assertThatIllegalArgumentException().isThrownBy {
                newNode(CordaX500Name(commonName = "Regulator", organisation = "R3CEV", organisationUnit = "Eric", locality = "Newcastle", country = "GB", state = null))().getOrThrow()
            }
        }
    }
    /** **** **/

    @Test(timeout=300_000)
	fun `driver allows reusing names of nodes that have been stopped`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodeA = newNode(DUMMY_BANK_A_NAME)().getOrThrow()
            nodeA.stop()
            assertThatCode { newNode(DUMMY_BANK_A_NAME)().getOrThrow() }.doesNotThrowAnyException()
        }
    }


    @Test(timeout=300_000)
	fun `driver waits for in-process nodes to finish`() {
        fun NodeHandle.stopQuietly() = try {
            stop()
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun DriverDSL.newNode(name: CordaX500Name): () -> CordaFuture<NodeHandle> = { startNode(NodeParameters(providedName = name)) }
}
