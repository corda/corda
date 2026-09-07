package net.corda.node.services.network

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.core.crypto.Crypto
import net.corda.core.internal.NODE_INFO_DIRECTORY
import net.corda.core.node.services.KeyManagementService
import net.corda.coretesting.internal.createNodeInfoAndSigned
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.node.internal.MockKeyManagementService
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.observers.TestSubscriber
import rx.schedulers.TestScheduler
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries
import kotlin.test.assertEquals

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
        // Register providers before creating Jimfs filesystem. JimFs creates an SSHD instance which
        // register BouncyCastle and EdDSA provider separately, which wrecks havoc.
        Crypto.registerProviders()

        nodeInfoAndSigned = createNodeInfoAndSigned(ALICE_NAME)
        val identityService = makeTestIdentityService()
        keyManagementService = MockKeyManagementService(identityService)
        nodeInfoWatcher = NodeInfoWatcher(tempFolder.root.toPath(), scheduler)
        nodeInfoPath = tempFolder.root.toPath() / NODE_INFO_DIRECTORY
    }

    @Test(timeout=300_000)
	fun `save a NodeInfo`() {
        assertThat(nodeInfoFiles()).isEmpty()

        NodeInfoWatcher.saveToFile(tempFolder.root.toPath(), nodeInfoAndSigned)

        val nodeInfoFiles = nodeInfoFiles()
        assertThat(nodeInfoFiles).hasSize(1)
        // Just check that something is written, another tests verifies that the written value can be read back.
        assertThat(nodeInfoFiles[0].fileSize()).isGreaterThan(0)
    }

    private fun nodeInfoFiles(): List<Path> {
        return tempFolder.root.toPath().useDirectoryEntries { paths ->
            paths.filter { it.name.startsWith(NODE_INFO_FILE_NAME_PREFIX) }.toList()
        }
    }

    @Test(timeout=300_000)
	fun `save a NodeInfo to JimFs`() {
        val jimFs = Jimfs.newFileSystem(Configuration.unix())
        val jimFolder = jimFs.getPath("/nodeInfo").createDirectories()
        NodeInfoWatcher.saveToFile(jimFolder, nodeInfoAndSigned)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
    fun `ignore tmp files`() {
        nodeInfoPath.createDirectories()

        // Start polling with an empty folder.
        val subscription = nodeInfoWatcher.nodeInfoUpdates().subscribe(testSubscriber)
        try {
            // Ensure the watch service is started.
            advanceTime()


            // create file
            // boohoo, we shouldn't create real files, instead mock Path
            val file = NodeInfoWatcher.saveToFile(nodeInfoPath, nodeInfoAndSigned)
            Files.move(file, Paths.get("$file.tmp"))

            advanceTime()

            // Check no nodeInfos are read.
            assertEquals(0, testSubscriber.onNextEvents.distinct().flatten().size)
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
