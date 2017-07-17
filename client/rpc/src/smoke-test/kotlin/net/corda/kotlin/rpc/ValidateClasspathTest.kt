package net.corda.kotlin.rpc

import net.corda.core.internal.div
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateClasspathTest {
    @Test
    fun `node not on classpath`() {
        val paths = System.getProperty("java.class.path").split(File.pathSeparatorChar).map { Paths.get(it) }
        // First find core so that if node is there, it's in the form we expect:
        assertFalse(paths.filter { it.contains("core" / "build") }.isEmpty())
        assertTrue(paths.filter { it.contains("node" / "build") }.isEmpty())
    }
}

private fun Path.contains(that: Path): Boolean {
    val size = that.nameCount
    (0..nameCount - size).forEach {
        if (subpath(it, it + size) == that) return true
    }
    return false
}
