package net.corda.sandbox.source

import net.corda.sandbox.analysis.AnalysisContext
import net.corda.sandbox.analysis.ClassResolver
import net.corda.sandbox.analysis.SourceLocation
import net.corda.sandbox.messages.Message
import net.corda.sandbox.messages.Severity
import net.corda.sandbox.rewiring.SandboxClassLoadingException
import net.corda.sandbox.utilities.loggerFor
import org.objectweb.asm.ClassReader
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

/**
 * Customizable class loader that allows the user to explicitly specify additional JARs and directories to scan.
 *
 * @param paths The directories and explicit JAR files to scan.
 * @property classResolver The resolver to use to derive the original name of a requested class.
 * @property resolvedUrls The resolved URLs that get passed to the underlying class loader.
 */
open class SourceClassLoader(
        paths: List<Path>,
        private val classResolver: ClassResolver,
        val resolvedUrls: Array<URL> = resolvePaths(paths)
) : URLClassLoader(resolvedUrls, SourceClassLoader::class.java.classLoader) {

    /**
     * Open a [ClassReader] for the provided class name.
     */
    fun classReader(
            className: String, context: AnalysisContext, origin: String? = null
    ): ClassReader {
        val originalName = classResolver.reverse(className.replace('.', '/'))
        return try {
            logger.trace("Opening ClassReader for class {}...", originalName)
            getResourceAsStream("$originalName.class").use {
                ClassReader(it)
            }
        } catch (exception: IOException) {
            context.messages.add(Message(
                    message ="Class file not found; $originalName.class",
                    severity = Severity.ERROR,
                    location = SourceLocation(origin ?: "")
            ))
            logger.error("Failed to open ClassReader for class", exception)
            throw SandboxClassLoadingException(context.messages, context.classes)
        }
    }

    /**
     * Find and load the class with the specified name from the search path.
     */
    override fun findClass(name: String): Class<*> {
        logger.trace("Finding class {}...", name)
        val originalName = classResolver.reverseNormalized(name)
        return super.findClass(originalName)
    }

    /**
     * Load the class with the specified binary name.
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        logger.trace("Loading class {}, resolve={}...", name, resolve)
        val originalName = classResolver.reverseNormalized(name)
        return super.loadClass(originalName, resolve)
    }

    private companion object {

        private val logger = loggerFor<SourceClassLoader>()

        private fun resolvePaths(paths: List<Path>): Array<URL> {
            return paths.map(this::expandPath).flatMap {
                when {
                    !Files.exists(it) -> throw FileNotFoundException("File not found; $it")
                    Files.isDirectory(it) -> {
                        listOf(it.toURL()) + Files.list(it).filter { isJarFile(it) }.map { it.toURL() }.toList()
                    }
                    Files.isReadable(it) && isJarFile(it) -> listOf(it.toURL())
                    else -> throw IllegalArgumentException("Expected JAR or class file, but found $it")
                }
            }.apply {
                logger.trace("Resolved paths: {}", this)
            }.toTypedArray()
        }

        private fun expandPath(path: Path): Path {
            val pathString = path.toString()
            if (pathString.startsWith("~/")) {
                return homeDirectory.resolve(pathString.removePrefix("~/"))
            }
            return path
        }

        private fun isJarFile(path: Path) = path.toString().endsWith(".jar", true)

        private fun Path.toURL() = this.toUri().toURL()

        private val homeDirectory: Path
            get() = Paths.get(System.getProperty("user.home"))

    }

}