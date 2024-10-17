package net.corda.nodeapi.internal.network

import net.corda.core.internal.NODE_INFO_DIRECTORY
import net.corda.testing.common.internal.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.schedulers.TestScheduler
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeBytes

class NodeInfoFilesCopierTest {
    companion object {
        private const val ORGANIZATION = "Organization"
        private const val NODE_1_PATH = "node1"
        private const val NODE_2_PATH = "node2"

        private val content = "blah".toByteArray(Charsets.UTF_8)
        private const val GOOD_NODE_INFO_NAME = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}test"
        private const val GOOD_NODE_INFO_NAME_2 = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}anotherNode"
        private const val BAD_NODE_INFO_NAME = "something"
    }

    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val rootPath get() = folder.root.toPath()
    private val scheduler = TestScheduler()

    private fun nodeDir(nodeBaseDir: String): Path = rootPath / nodeBaseDir / ORGANIZATION.lowercase()

    private val node1RootPath by lazy { nodeDir(NODE_1_PATH) }
    private val node2RootPath by lazy { nodeDir(NODE_2_PATH) }
    private val node1AdditionalNodeInfoPath by lazy { node1RootPath.resolve(NODE_INFO_DIRECTORY) }
    private val node2AdditionalNodeInfoPath by lazy { node2RootPath.resolve(NODE_INFO_DIRECTORY) }

    private lateinit var nodeInfoFilesCopier: NodeInfoFilesCopier

    @Before
    fun setUp() {
        nodeInfoFilesCopier = NodeInfoFilesCopier(scheduler)
    }

    @Test(timeout=300_000)
	fun `files created before a node is started are copied to that node`() {
        // Configure the first node.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        // Ensure directories are created.
        advanceTime()

        // Create 2 files, a nodeInfo and another file in node1 folder.
        (node1RootPath / GOOD_NODE_INFO_NAME).writeBytes(content)
        (node1RootPath / BAD_NODE_INFO_NAME).writeBytes(content)

        // Configure the second node.
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        eventually(Duration.ofMinutes(1)) {
            // Check only one file is copied.
            checkDirectoryContainsSingleFile(node2AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test(timeout=300_000)
	fun `polling of running nodes`() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        // Create 2 files, one of which to be copied, in a node root path.
        (node2RootPath / GOOD_NODE_INFO_NAME).writeBytes(content)
        (node2RootPath / BAD_NODE_INFO_NAME).writeBytes(content)
        advanceTime()

        eventually(Duration.ofMinutes(1)) {
            // Check only one file is copied to the other node.
            checkDirectoryContainsSingleFile(node1AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test(timeout=300_000)
	fun `remove nodes`() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        // Create a file, in node 2 root path.
        (node2RootPath / GOOD_NODE_INFO_NAME).writeBytes(content)
        advanceTime()

        // Remove node 2
        nodeInfoFilesCopier.removeConfig(node2RootPath)

        // Create another file in node 2 directory.
        (node2RootPath / GOOD_NODE_INFO_NAME).writeBytes(content)
        advanceTime()

        eventually(Duration.ofMinutes(1)) {
            // Check only one file is copied to the other node.
            checkDirectoryContainsSingleFile(node1AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test(timeout=300_000)
	fun clear() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        nodeInfoFilesCopier.reset()

        advanceTime()
        (node2RootPath / GOOD_NODE_INFO_NAME_2).writeBytes(content)

        // Give some time to the filesystem to report the change.
        eventually {
            assertThat(node1AdditionalNodeInfoPath.listDirectoryEntries()).isEmpty()
        }
    }

    private fun advanceTime() {
        scheduler.advanceTimeBy(1, TimeUnit.HOURS)
    }

    private fun checkDirectoryContainsSingleFile(path: Path, filename: String) {
        val files = path.listDirectoryEntries()
        assertThat(files).hasSize(1)
        assertThat(files[0].name).isEqualTo(filename)
    }
}