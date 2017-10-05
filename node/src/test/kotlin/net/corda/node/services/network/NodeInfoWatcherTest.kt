package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.testing.*
import net.corda.testing.node.MockKeyManagementService
import net.corda.testing.node.NodeBasedTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.observers.TestSubscriber
import rx.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.contentOf
import java.nio.file.Path

class NodeInfoWatcherTest : NodeBasedTest() {

    @Rule @JvmField var folder = TemporaryFolder()

    lateinit var keyManagementService: KeyManagementService
    lateinit var nodeInfoPath: Path
    val scheduler = TestScheduler();
    val testSubscriber = TestSubscriber<NodeInfo>()

    // Object under test
    lateinit var nodeInfoWatcher: NodeInfoWatcher

    companion object {
        val nodeInfoFileRegex = Regex("nodeInfo\\-.*")
        val nodeInfo = NodeInfo(listOf(), listOf(getTestPartyAndCertificate(ALICE)), 0, 0)
    }

    @Before
    fun start() {
        val identityService = InMemoryIdentityService(trustRoot = DEV_TRUST_ROOT)
        keyManagementService = MockKeyManagementService(identityService, ALICE_KEY)
        nodeInfoWatcher = NodeInfoWatcher(folder.root.toPath(), scheduler)
        nodeInfoPath = folder.root.toPath() / CordformNode.NODE_INFO_DIRECTORY
    }

    @Test
    fun `save a NodeInfo`() {
        NodeInfoWatcher.saveToFile(folder.root.toPath(), nodeInfo, keyManagementService)

        assertEquals(1, folder.root.list().size)
        val fileName = folder.root.list()[0]
        assertTrue(fileName.matches(nodeInfoFileRegex))
        val file = (folder.root.path / fileName).toFile()
        // Just check that something is written, another tests verifies that the written value can be read back.
        assertThat(contentOf(file)).isNotEmpty()
    }

    @Test
    fun `load an empty Directory`() {
        nodeInfoPath.createDirectories()

        nodeInfoWatcher.nodeInfoUpdates()
                .subscribe(testSubscriber)

        val readNodes = testSubscriber.onNextEvents.distinct()
        scheduler.advanceTimeBy(1, TimeUnit.HOURS)
        assertEquals(0, readNodes.size)
    }

    @Test
    fun `load a non empty Directory`() {
        createNodeInfoFileInPath(nodeInfo)

        nodeInfoWatcher.nodeInfoUpdates()
                .subscribe(testSubscriber)

        val readNodes = testSubscriber.onNextEvents.distinct()

        assertEquals(1, readNodes.size)
        assertEquals(nodeInfo, readNodes.first())
    }

    @Test
    fun `polling folder`() {
        nodeInfoPath.createDirectories()

        // Start polling with an empty folder.
        nodeInfoWatcher.nodeInfoUpdates()
                .subscribe(testSubscriber)
        // Ensure the watch service is started.
        scheduler.advanceTimeBy(1, TimeUnit.HOURS)

        // Check no nodeInfos are read.
        assertEquals(0, testSubscriber.valueCount)
        createNodeInfoFileInPath(nodeInfo)

        scheduler.advanceTimeBy(1, TimeUnit.HOURS)

        // The same folder can be reported more than once, so take unique values.
        val readNodes = testSubscriber.onNextEvents.distinct()
        assertEquals(1, readNodes.size)
        assertEquals(nodeInfo, readNodes.first())
    }

    // Write a nodeInfo under the right path.
    private fun createNodeInfoFileInPath(nodeInfo: NodeInfo) {
        NodeInfoWatcher.saveToFile(nodeInfoPath, nodeInfo, keyManagementService)
    }
}
