package net.corda.sandbox.source

import java.io.InputStream
import java.nio.file.Path

/**
 * The source of one or more compiled Java classes.
 *
 * @property qualifiedClassName The fully qualified class name.
 * @property origin The origin of the class source, if any.
 */
@Suppress("unused")
open class ClassSource protected constructor(
        open val qualifiedClassName: String = "",
        val origin: String? = null
): Iterable<InputStream> {

    /**
     * Return an iterator over the classes available in this class source.
     */
    override fun iterator(): Iterator<InputStream> {
        return emptyList<InputStream>().iterator()
    }

    /**
     * Check if path is referring to a JAR file.
     */
    protected fun isJar(path: Path) =
            path.fileName.toString().endsWith(".jar", true)

    /**
     * Check if path is referring to a class file.
     */
    protected fun isClass(path: Path) =
            isClass(path.fileName.toString())

    /**
     * Check if path is referring to a class file.
     */
    private fun isClass(path: String) =
            path.endsWith(".class", true)

    companion object {

        /**
         * Instantiate a [ClassSource] from a fully qualified class name.
         */
        fun fromClassName(className: String, origin: String? = null) =
                ClassSource(className, origin)

        /**
         * Instantiate a [ClassSource] from a file on disk.
         */
        fun fromPath(path: Path) = ClassPathSource(path)

        /**
         * Instantiate a [ClassSource] from an input stream.
         */
        fun fromStream(stream: InputStream) = ClassInputStreamSource(stream)

    }

}