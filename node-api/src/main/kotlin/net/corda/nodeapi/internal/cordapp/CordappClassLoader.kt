package net.corda.nodeapi.internal.cordapp

import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.security.CodeSource
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.toPath

/**
 * The class loader of a node's CorDapps: a [URLClassLoader] over the CorDapp jars which converts the classes that were built against
 * `javax.persistence` (CorDapps built for Corda 4.14 or earlier) to `jakarta.persistence`, which is what the node's Hibernate 7 reads, as
 * it defines them (see [JavaxPersistenceTransformer]). The jars themselves are not touched, so their hashes and signatures are unaffected,
 * and a class that is not built for `javax.persistence` alone is defined from its original bytes.
 *
 * Classes are defined with the same code source (jar location and signers) and package definition as [URLClassLoader] would give them,
 * because the node maps classes to CorDapps by their code source location. URLs that are not jar files on disk are served by
 * [URLClassLoader] as they are, without conversion.
 */
class CordappClassLoader(urls: Array<URL>, parent: ClassLoader?) : URLClassLoader(urls, parent) {
    companion object {
        private val logger = contextLogger()

        init {
            registerAsParallelCapable()
        }
    }

    private class Jar(val url: URL, val path: Path) {
        val file: JarFile by lazy { JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion()) }
    }

    private val jars: List<Jar> = urls.mapNotNull { url ->
        val path = if (url.protocol == "file") url.toURI().toPath() else return@mapNotNull null
        if (path.isRegularFile() && path.toString().endsWith(".jar", ignoreCase = true)) Jar(url, path) else null
    }
    private val announcedJars = ConcurrentHashMap.newKeySet<Path>()
    private val _convertedClassNames = ConcurrentHashMap.newKeySet<String>()

    /** The names of the classes converted from `javax.persistence` to `jakarta.persistence` so far. */
    val convertedClassNames: Set<String> get() = _convertedClassNames

    override fun findClass(name: String): Class<*> {
        val entryName = name.replace('.', '/') + ".class"
        for (jar in jars) {
            val entry = jar.file.getJarEntry(entryName) ?: continue
            val originalBytes = jar.file.getInputStream(entry).use { it.readBytes() }
            val bytes = if (JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(originalBytes)) {
                if (announcedJars.add(jar.path)) {
                    logger.info("CorDapp ${jar.path} was built for Corda 4.14 or earlier (javax.persistence); its classes are converted " +
                            "to jakarta.persistence for Hibernate 7 as they are loaded")
                }
                JavaxPersistenceTransformer.transform(originalBytes, name).also { _convertedClassNames += name }
            } else {
                originalBytes
            }
            definePackageIfAbsent(name, jar)
            // The signers of a jar entry are only known once the entry has been read in full, which it has been above.
            return defineClass(name, bytes, 0, bytes.size, CodeSource(jar.url, entry.codeSigners))
        }
        return super.findClass(name)
    }

    private fun definePackageIfAbsent(className: String, jar: Jar) {
        val packageName = className.substringBeforeLast('.', "")
        if (packageName.isEmpty() || getDefinedPackage(packageName) != null) return
        try {
            val manifest = jar.file.manifest
            if (manifest != null) definePackage(packageName, manifest, jar.url) else definePackage(packageName, null, null, null, null, null, null, null)
        } catch (e: IllegalArgumentException) {
            // Defined concurrently by another thread, which is fine.
        }
    }

    override fun close() {
        try {
            jars.forEach { jar -> runCatching { jar.file.close() } }
        } finally {
            super.close()
        }
    }
}
