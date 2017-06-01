package net.corda.node.services.transactions

import net.corda.core.exists
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathManagerTests {
    @Test
    fun `path deleted when manager closed`() {
        val manager = PathManager(Files.createTempFile(javaClass.simpleName, null))
        val leakedPath = manager.use {
            it.path.also { assertTrue(it.exists()) }
        }
        assertFalse(leakedPath.exists())
        assertFailsWith(IllegalStateException::class) { manager.path }
    }

    @Test
    fun `path deleted when handle closed`() {
        val handle = PathManager(Files.createTempFile(javaClass.simpleName, null)).use {
            it.handle()
        }
        val leakedPath = handle.use {
            it.path.also { assertTrue(it.exists()) }
        }
        assertFalse(leakedPath.exists())
        assertFailsWith(IllegalStateException::class) { handle.path }
    }
}
