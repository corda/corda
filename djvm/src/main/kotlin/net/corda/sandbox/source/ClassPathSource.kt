package net.corda.sandbox.source

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Source of compiled Java classes.
 *
 * @property path The path of a class source in the file system, a JAR or a class.
 */
class ClassPathSource(
        private val path: Path
) : ClassSource() {

    override fun iterator(): Iterator<InputStream> {
        return when {
            isClass(path) -> {
                listOf(Files.newInputStream(path)).iterator()
            }
            isJar(path) -> {
                JarInputStreamIterator(Files.newInputStream(path))
            }
            else -> {
                throw IllegalArgumentException("Invalid file extension '$path'")
            }
        }
    }

}
