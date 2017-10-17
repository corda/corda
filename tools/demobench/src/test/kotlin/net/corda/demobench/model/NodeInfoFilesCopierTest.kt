package net.corda.demobench.model

import net.corda.cordform.CordformNode
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.eventually
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.schedulers.TestScheduler
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.streams.toList
import kotlin.test.assertEquals

/**
 * tests for [NodeInfoFilesCopier]
 */
class NodeInfoFilesCopierTest {

    @Rule @JvmField var folder = TemporaryFolder()
    private val rootPath get() = folder.root.toPath()
    private val scheduler = TestScheduler()
    companion object {
        private const val ORGANIZATION = "Organization"
        private const val NODE_1_PATH = "node1"
        private const val NODE_2_PATH = "node2"

        private val content = "blah".toByteArray(Charsets.UTF_8)
        private val GOOD_NODE_INFO_NAME = "nodeInfo-test"
        private val GOOD_NODE_INFO_NAME_2 = "nodeInfo-anotherNode"
        private val BAD_NODE_INFO_NAME = "something"
        private val legalName = CordaX500Name(organisation = ORGANIZATION, locality = "Nowhere", country = "GB")
        private val hostAndPort = NetworkHostAndPort("localhost", 1)
    }

    private fun nodeDir(nodeBaseDir : String) = rootPath.resolve(nodeBaseDir).resolve(ORGANIZATION.toLowerCase())

    private val node1Config by lazy { createConfig(NODE_1_PATH) }
    private val node2Config by lazy { createConfig(NODE_2_PATH) }
    private val node1RootPath by lazy { nodeDir(NODE_1_PATH) }
    private val node2RootPath by lazy { nodeDir(NODE_2_PATH) }
    private val node1AdditionalNodeInfoPath by lazy { node1RootPath.resolve(CordformNode.NODE_INFO_DIRECTORY) }
    private val node2AdditionalNodeInfoPath by lazy { node2RootPath.resolve(CordformNode.NODE_INFO_DIRECTORY) }

    lateinit var nodeInfoFilesCopier: NodeInfoFilesCopier

    @Before
    fun setUp() {
        nodeInfoFilesCopier = NodeInfoFilesCopier(scheduler)
    }

    @Test
    fun `files created before a node is started are copied to that node`() {
        // Configure the first node.
        nodeInfoFilesCopier.addConfig(node1Config)
        // Ensure directories are created.
        advanceTime()

        // Create 2 files, a nodeInfo and another file in node1 folder.
        Files.write(node1RootPath.resolve(GOOD_NODE_INFO_NAME), content)
        Files.write(node1RootPath.resolve(BAD_NODE_INFO_NAME), content)

        // Configure the second node.
        nodeInfoFilesCopier.addConfig(node2Config)
        advanceTime()

        eventually<AssertionError, Unit>(Duration.ofMinutes(1)) {
            // Check only one file is copied.
            checkDirectoryContainsSingleFile(node2AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test
    fun `polling of running nodes`() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1Config)
        nodeInfoFilesCopier.addConfig(node2Config)
        advanceTime()

        // Create 2 files, one of which to be copied, in a node root path.
        Files.write(node2RootPath.resolve(GOOD_NODE_INFO_NAME), content)
        Files.write(node2RootPath.resolve(BAD_NODE_INFO_NAME), content)
        advanceTime()

        eventually<AssertionError, Unit>(Duration.ofMinutes(1)) {
            // Check only one file is copied to the other node.
            checkDirectoryContainsSingleFile(node1AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test
    fun `remove nodes`() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1Config)
        nodeInfoFilesCopier.addConfig(node2Config)
        advanceTime()

        // Create a file, in node 2 root path.
        Files.write(node2RootPath.resolve(GOOD_NODE_INFO_NAME), content)
        advanceTime()

        // Remove node 2
        nodeInfoFilesCopier.removeConfig(node2Config)

        // Create another file in node 2 directory.
        Files.write(node2RootPath.resolve(GOOD_NODE_INFO_NAME_2), content)
        advanceTime()

        eventually<AssertionError, Unit>(Duration.ofMinutes(1)) {
            // Check only one file is copied to the other node.
            checkDirectoryContainsSingleFile(node1AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test
    fun `clear`() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1Config)
        nodeInfoFilesCopier.addConfig(node2Config)
        advanceTime()

        nodeInfoFilesCopier.reset()

        advanceTime()
        Files.write(node2RootPath.resolve(GOOD_NODE_INFO_NAME_2), content)

        // Give some time to the filesystem to report the change.
        Thread.sleep(100)
        assertEquals(0, Files.list(node1AdditionalNodeInfoPath).toList().size)
    }

    private fun advanceTime() {
        scheduler.advanceTimeBy(1, TimeUnit.HOURS)
    }

    private fun checkDirectoryContainsSingleFile(path: Path, filename: String) {
        assertEquals(1, Files.list(path).toList().size)
        val onlyFileName = Files.list(path).toList().first().fileName.toString()
        assertEquals(filename, onlyFileName)
    }

    private fun createConfig(relativePath: String) =
            NodeConfigWrapper(rootPath.resolve(relativePath),
                    NodeConfig(myLegalName = legalName,
                            p2pAddress = hostAndPort,
                            rpcAddress = hostAndPort,
                            webAddress = hostAndPort,
                            h2port = -1,
                            notary = null,
                            networkMapService = null,
                            rpcUsers = listOf()))
}