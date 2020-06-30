package net.corda.node.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith

class NodeStartupTest {
    @Test(timeout=300_000)
	fun `test that you cant start two nodes in the same directory`() {
        val dir = Files.createTempDirectory("node_startup_test")
        val latch = CountDownLatch(1)

        thread(start = true) {
            val node = NodeStartup()
            assertThat(node.isNodeRunningAt(dir)).isTrue()
            latch.countDown()
        }

        // wait until the file has been created on the other thread
        latch.await()

        // Check that I can't start up another node in the same directory
        val anotherNode = NodeStartup()
        assertFailsWith<OverlappingFileLockException> { anotherNode.isNodeRunningAt(dir) }
    }
}