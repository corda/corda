package net.corda.errorUtilities

import java.net.URLClassLoader
import java.nio.file.Path

/**
 * A class for reading and processing error code resource bundles from a given directory.
 */
class ErrorResourceUtilities {

    companion object {
        private const val ERROR_INFO_RESOURCE = ".*ErrorInfo.*"

        /**
         * List all resource bundle names in a given directory
         */
        fun listResourceNames(location: Path) : List<String> {
            return location.toFile().walkTopDown().filter {
                it.name.matches("[^_]*\\.properties".toRegex()) && !it.name.matches(ERROR_INFO_RESOURCE.toRegex())
            }.map {
                it.nameWithoutExtension
            }.toList()
        }

        /**
         * List all resource files in a given directory
         */
        fun listResourceFiles(location: Path) : List<String> {
            return location.toFile().walkTopDown().filter {
                it.name.matches(".*\\.properties".toRegex())
            }.map { it.name }.toList()
        }

        /**
         * Create a classloader with all URLs in a given directory
         */
        fun loaderFromDirectory(location: Path) : URLClassLoader {
//            val urls = location.toFile().walkTopDown().map { it.toURI().toURL() }.asIterable().toList().toTypedArray()
            val urls = arrayOf(location.toUri().toURL())
            val sysLoader = ClassLoader.getSystemClassLoader()
            return URLClassLoader(urls, sysLoader)
        }
    }
}