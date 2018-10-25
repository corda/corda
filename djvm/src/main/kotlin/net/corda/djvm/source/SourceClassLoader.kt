@file:JvmName("SourceClassLoaderTools")
package net.corda.djvm.source

import net.corda.djvm.analysis.AnalysisConfiguration.Companion.SANDBOX_PREFIX
import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.analysis.ClassResolver
import net.corda.djvm.analysis.ExceptionResolver.Companion.getDJVMExceptionOwner
import net.corda.djvm.analysis.ExceptionResolver.Companion.isDJVMException
import net.corda.djvm.analysis.SourceLocation
import net.corda.djvm.code.asResourcePath
import net.corda.djvm.messages.Message
import net.corda.djvm.messages.Severity
import net.corda.djvm.rewiring.SandboxClassLoadingException
import net.corda.djvm.utilities.loggerFor
import org.objectweb.asm.ClassReader
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

abstract class AbstractSourceClassLoader(
    paths: List<Path>,
    private val classResolver: ClassResolver,
    parent: ClassLoader?
) : URLClassLoader(resolvePaths(paths), parent) {

    /**
     * Open a [ClassReader] for the provided class name.
     */
    fun classReader(
            className: String, context: AnalysisContext, origin: String? = null
    ): ClassReader {
        val originalName = classResolver.reverse(className.asResourcePath)

        fun throwClassLoadingError(): Nothing {
            context.messages.provisionalAdd(Message(
                message ="Class file not found; $originalName.class",
                severity = Severity.ERROR,
                location = SourceLocation(origin ?: "")
            ))
            throw SandboxClassLoadingException(context)
        }

        return try {
            logger.trace("Opening ClassReader for class {}...", originalName)
            getResourceAsStream("$originalName.class")?.use {
                ClassReader(it)
            } ?: run(::throwClassLoadingError)
        } catch (exception: IOException) {
            throwClassLoadingError()
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
        val originalName = classResolver.reverseNormalized(name).let { n ->
            // A synthetic exception should be mapped back to its
            // corresponding exception in the original hierarchy.
            if (isDJVMException(n)) {
                getDJVMExceptionOwner(n)
            } else {
                n
            }
        }
        return super.loadClass(originalName, resolve)
    }

    protected companion object {
        @JvmStatic
        protected val logger = loggerFor<SourceClassLoader>()
    }
}

/**
 * Class loader to manage an optional JAR of replacement Java APIs.
 * @param bootstrapJar The location of the JAR containing the Java APIs.
 */
class BootstrapClassLoader(
    bootstrapJar: Path
) : URLClassLoader(resolvePaths(listOf(bootstrapJar)), null) {

    /**
     * Only search our own jars for the given resource.
     */
    override fun getResource(name: String): URL? = findResource(name)
}

/**
 * Class loader that only provides our built-in sandbox classes.
 * @param classResolver The resolver to use to derive the original name of a requested class.
 */
class SandboxSourceClassLoader(
    classResolver: ClassResolver,
    private val bootstrap: BootstrapClassLoader
) : AbstractSourceClassLoader(emptyList(), classResolver, SandboxSourceClassLoader::class.java.classLoader) {

    /**
     * Always check the bootstrap classloader first. If we're requesting
     * built-in sandbox classes then delegate to our parent classloader,
     * otherwise deny the request.
     */
    override fun getResource(name: String): URL? {
        val resource = bootstrap.findResource(name)
        if (resource != null) {
            return resource
        } else if (isJvmInternal(name)) {
            logger.error("Denying request for actual {}", name)
            return null
        }

        return if (name.startsWith(SANDBOX_PREFIX)) {
            parent.getResource(name)
        } else {
            null
        }
    }
}

/**
 * Customizable class loader that allows the user to explicitly specify additional JARs and directories to scan.
 *
 * @param paths The directories and explicit JAR files to scan.
 * @property classResolver The resolver to use to derive the original name of a requested class.
 * @property bootstrap The [BootstrapClassLoader] containing the Java APIs for the sandbox.
 */
class SourceClassLoader(
    paths: List<Path>,
    classResolver: ClassResolver,
    private val bootstrap: BootstrapClassLoader? = null
) : AbstractSourceClassLoader(paths, classResolver, SourceClassLoader::class.java.classLoader) {

    /**
     * First check the bootstrap classloader, if we have one.
     * Otherwise check our parent classloader, followed by
     * the user-supplied jars.
     */
    override fun getResource(name: String): URL? {
        if (bootstrap != null) {
            val resource = bootstrap.findResource(name)
            if (resource != null) {
                return resource
            } else if (isJvmInternal(name)) {
                logger.error("Denying request for actual {}", name)
                return null
            }
        }

        return parent?.getResource(name) ?: findResource(name)
    }

    /**
     * Deny all requests for DJVM classes from any user-supplied jars.
     */
    override fun findResource(name: String): URL? {
        return if (name.startsWith("net/corda/djvm/")) null else super.findResource(name)
    }

}

private fun resolvePaths(paths: List<Path>): Array<URL> {
    return paths.map(::expandPath).flatMap {
        when {
            !Files.exists(it) -> throw FileNotFoundException("File not found; $it")
            Files.isDirectory(it) -> {
                listOf(it.toURL()) + Files.list(it).filter(::isJarFile).map { jar -> jar.toURL() }.toList()
            }
            Files.isReadable(it) && isJarFile(it) -> listOf(it.toURL())
            else -> throw IllegalArgumentException("Expected JAR or class file, but found $it")
        }
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

private fun Path.toURL(): URL = this.toUri().toURL()

private val homeDirectory: Path
    get() = Paths.get(System.getProperty("user.home"))

/**
 * Does [name] exist within any of the packages reserved for Java itself?
 */
private fun isJvmInternal(name: String): Boolean = name.startsWith("java/")
        || name.startsWith("javax/")
        || name.startsWith("com/sun/")
        || name.startsWith("sun/")
        || name.startsWith("jdk/")
