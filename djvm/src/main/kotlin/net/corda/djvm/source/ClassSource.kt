package net.corda.djvm.source

import java.nio.file.Path

/**
 * The source of one or more compiled Java classes.
 *
 * @property qualifiedClassName The fully qualified class name.
 * @property origin The origin of the class source, if any.
 */
class ClassSource private constructor(
        val qualifiedClassName: String = "",
        val origin: String? = null
) {

    companion object {

        /**
         * Instantiate a [ClassSource] from a fully qualified class name.
         */
        fun fromClassName(className: String, origin: String? = null) =
                ClassSource(className, origin)

        /**
         * Instantiate a [ClassSource] from a file on disk.
         */
        fun fromPath(path: Path) = PathClassSource(path)

        /**
         * Check if path is referring to a JAR file.
         */
        fun isJar(path: Path) =
                path.fileName.toString().endsWith(".jar", true)

        /**
         * Check if path is referring to a class file.
         */
        fun isClass(path: Path) =
                path.fileName.toString().endsWith(".class", true)

    }

}