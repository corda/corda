package net.corda.errorUtilities

import java.net.URLClassLoader
import java.nio.file.Path

/**
 * A class for reading and processing error code resource bundles from a given directory.
 */
class ErrorResourceUtilities {

    companion object {
        private val ERROR_INFO_RESOURCE_REGEX= ".*ErrorInfo.*".toRegex()
        private val DEFAULT_RESOURCE_FILE_REGEX = "[^_]*\\.properties".toRegex()
        private val PROPERTIES_FILE_REGEX = ".*\\.properties".toRegex()

        /**
         * List all resource bundle names in a given directory
         */
        fun listResourceNames(location: Path) : List<String> {
            return location.toFile().walkTopDown().filter {
                it.name.matches(DEFAULT_RESOURCE_FILE_REGEX) && !it.name.matches(ERROR_INFO_RESOURCE_REGEX)
            }.map {
                it.nameWithoutExtension
            }.toList()
        }

        /**
         * List all resource files in a given directory
         */
        fun listResourceFiles(location: Path) : List<String> {
            return location.toFile().walkTopDown().filter {
                it.name.matches(PROPERTIES_FILE_REGEX)
            }.map { it.name }.toList()
        }

        /**
         * Create a classloader with all URLs in a given directory
         */
        fun loaderFromDirectory(location: Path) : URLClassLoader {
            val urls = arrayOf(location.toUri().toURL())
            val sysLoader = ClassLoader.getSystemClassLoader()
            return URLClassLoader(urls, sysLoader)
        }
    }
}