package net.corda.node.services.transactions

import net.corda.core.internal.exists
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathManagerTests {
    private class MyPathManager : PathManager<MyPathManager>(Files.createTempFile(MyPathManager::class.simpleName, null))

    @Test
    fun `path deleted when manager closed`() {
        val manager = MyPathManager()
        val leakedPath = manager.use {
            it.path.also { assertTrue(it.exists()) }
        }
        assertFalse(leakedPath.exists())
        assertFailsWith(IllegalStateException::class) { manager.path }
    }

    @Test
    fun `path deleted when handle closed`() {
        val handle = MyPathManager().use {
            it.handle()
        }
        val leakedPath = handle.use {
            it.path.also { assertTrue(it.exists()) }
        }
        assertFalse(leakedPath.exists())
        assertFailsWith(IllegalStateException::class) { handle.path }
    }
}
