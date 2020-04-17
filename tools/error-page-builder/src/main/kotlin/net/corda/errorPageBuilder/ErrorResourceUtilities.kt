package net.corda.errorPageBuilder

import java.io.File
import java.net.URLClassLoader

/**
 * A class for reading and processing error code resource bundles from a given directory.
 */
class ErrorResourceUtilities(private val resourceLocation: File) {

    companion object {
        private const val ERROR_INFO_RESOURCE = ".*ErrorInfo.*"
    }

    fun listResources() : Iterator<String> {
        return resourceLocation.walkTopDown().filter {
            it.name.matches("[^_]*\\.properties".toRegex()) && !it.name.matches(ERROR_INFO_RESOURCE.toRegex())
        }.map {
            it.nameWithoutExtension
        }.iterator()
    }

    fun loaderForResources() : ClassLoader {
        val urls = resourceLocation.walkTopDown().map { it.toURI().toURL() }.asIterable().toList().toTypedArray()
        return URLClassLoader(urls)
    }
}