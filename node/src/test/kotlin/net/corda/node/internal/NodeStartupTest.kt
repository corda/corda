package net.corda.node.internal

import com.google.common.io.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith

class NodeStartupTest {
    @Test
    fun `test that you cant start two nodes in the same directory`() {
        val dir = Files.createTempDir().toPath()

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