package net.corda.node.services.network

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.size
import net.corda.core.node.services.KeyManagementService
import net.corda.nodeapi.internal.NODE_INFO_DIRECTORY
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.node.internal.MockKeyManagementService
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.observers.TestSubscriber
import rx.schedulers.TestScheduler
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeInfoWatcherTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val scheduler = TestScheduler()
    private val testSubscriber = TestSubscriber<List<NodeInfoUpdate>>()

    private lateinit var nodeInfoAndSigned: NodeInfoAndSigned
    private lateinit var nodeInfoPath: Path
    private lateinit var keyManagementService: KeyManagementService

    // Object under test
    private lateinit var nodeInfoWatcher: NodeInfoWatcher

    @Before
    fun start() {
        nodeInfoAndSigned = createNodeInfoAndSigned(ALICE_NAME)
        val identityService = makeTestIdentityService()
        keyManagementService = MockKeyManagementService(identityService)
        nodeInfoWatcher = NodeInfoWatcher(tempFolder.root.toPath(), scheduler)
        nodeInfoPath = tempFolder.root.toPath() / NODE_INFO_DIRECTORY
    }

    @Test
    fun `save a NodeInfo`() {
        assertEquals(0,
                tempFolder.root.list().filter { it.startsWith(NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX) }.size)
        NodeInfoWatcher.saveToFile(tempFolder.root.toPath(), nodeInfoAndSigned)

        val nodeInfoFiles = tempFolder.root.list().filter { it.startsWith(NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX) }
        assertEquals(1, nodeInfoFiles.size)
        val fileName = nodeInfoFiles.first()
        assertTrue(fileName.startsWith(NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX))
        val file = (tempFolder.root.path / fileName)
        // Just check that something is written, another tests verifies that the written value can be read back.
        assertThat(file.size).isGreaterThan(0)
    }

    @Test
    fun `save a NodeInfo to JimFs`() {
        val jimFs = Jimfs.newFileSystem(Configuration.unix())
        val jimFolder = jimFs.getPath("/nodeInfo").createDirectories()
        NodeInfoWatcher.saveToFile(jimFolder, nodeInfoAndSigned)
    }

    @Test
    fun `load an empty Directory`() {
        nodeInfoPath.createDirectories()

        val subscription = nodeInfoWatcher.nodeInfoUpdates().subscribe(testSubscriber)
        try {
            advanceTime()
            val readNodes = testSubscriber.onNextEvents.distinct().flatten()
            assertEquals(0, readNodes.size)
        } finally {
            subscription.unsubscribe()
        }
    }

    @Test
    fun `load a non empty Directory`() {
        createNodeInfoFileInPath()

        val subscription = nodeInfoWatcher.nodeInfoUpdates().subscribe(testSubscriber)
        advanceTime()

        try {
            val readNodes = testSubscriber.onNextEvents.distinct().flatten()
            assertEquals(1, readNodes.size)
            assertEquals(nodeInfoAndSigned.nodeInfo, (readNodes.first() as? NodeInfoUpdate.Add)?.nodeInfo)
        } finally {
            subscription.unsubscribe()
        }
    }

    @Test
    fun `polling folder`() {
        nodeInfoPath.createDirectories()

        // Start polling with an empty folder.
        val subscription = nodeInfoWatcher.nodeInfoUpdates().subscribe(testSubscriber)
        try {
            // Ensure the watch service is started.
            advanceTime()
            // Check no nodeInfos are read.

            assertEquals(0, testSubscriber.onNextEvents.distinct().flatten().size)
            createNodeInfoFileInPath()

            advanceTime()

            // We need the WatchService to report a change and that might not happen immediately.
            testSubscriber.awaitValueCount(1, 5, TimeUnit.SECONDS)
            // The same folder can be reported more than once, so take unique values.
            val readNodes = testSubscriber.onNextEvents.distinct().flatten()
            assertEquals(nodeInfoAndSigned.nodeInfo, (readNodes.first() as? NodeInfoUpdate.Add)?.nodeInfo)
        } finally {
            subscription.unsubscribe()
        }
    }

    private fun advanceTime() {
        scheduler.advanceTimeBy(1, TimeUnit.MINUTES)
    }

    // Write a nodeInfo under the right path.
    private fun createNodeInfoFileInPath() {
        NodeInfoWatcher.saveToFile(nodeInfoPath, nodeInfoAndSigned)
    }
}
