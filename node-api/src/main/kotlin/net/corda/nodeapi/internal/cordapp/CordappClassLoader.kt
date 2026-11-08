package net.corda.nodeapi.internal.cordapp

import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.CordappConversionException
import net.corda.nodeapi.internal.persistence.JavaxPersistenceConversionError
import net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.security.CodeSource
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.Attributes
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
 * because the node maps classes to CorDapps by their code source location, and package sealing (the `Sealed` manifest attribute) is
 * verified as [URLClassLoader] verifies it. The jar files among the URLs are searched first, in their order, and only then the other URLs
 * (directories, remote URLs), which [URLClassLoader] serves as they are, without conversion; the node only ever passes jars. A CorDapp
 * that bundles the `javax.persistence` API itself has those classes defined as they are too: renaming them would give them the wrong name.
 *
 * A class that cannot be converted (a Hibernate annotation Hibernate 7 cannot read, or a reference to a Hibernate class or member that
 * does not exist in Hibernate 7) fails to load with a [JavaxPersistenceConversionError]. [checkConvertible] finds such classes ahead of
 * time, over every class built for `javax.persistence` in every jar, so that the node refuses to start, naming all of them, rather than
 * failing in a flow when one is first used.
 */
class CordappClassLoader(urls: Array<URL>, parent: ClassLoader?) : URLClassLoader(urls, parent) {
    companion object {
        private val logger = contextLogger()
        private const val JAVAX_PERSISTENCE_PACKAGE = "javax.persistence."

        init {
            registerAsParallelCapable()
        }
    }

    private class Jar(val url: URL, val path: Path) {
        private val lazyFile = lazy { JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion()) }

        /** The jar, opened (and its signatures read) on first use. */
        val file: JarFile get() = lazyFile.value

        fun closeIfOpen() {
            if (lazyFile.isInitialized()) runCatching { lazyFile.value.close() }
        }
    }

    private val jars: List<Jar> = urls.mapNotNull { url ->
        val path = if (url.protocol == "file") url.toURI().toPath() else return@mapNotNull null
        if (path.isRegularFile() && path.toString().endsWith(".jar", ignoreCase = true)) Jar(url, path) else null
    }
    private val announcedJars = ConcurrentHashMap.newKeySet<Path>()
    private val _convertedClassNames = ConcurrentHashMap.newKeySet<String>()

    /** Where the node's Hibernate 7 is: the Hibernate references of a converted class are resolved against it. */
    private val hibernateClassLoader: ClassLoader = parent ?: ClassLoader.getSystemClassLoader()

    /** The names of the classes converted from `javax.persistence` to `jakarta.persistence` so far. */
    val convertedClassNames: Set<String> get() = _convertedClassNames

    override fun findClass(name: String): Class<*> {
        val entryName = name.replace('.', '/') + ".class"
        for (jar in jars) {
            val entry = jar.file.getJarEntry(entryName) ?: continue
            val originalBytes = jar.file.getInputStream(entry).use { it.readBytes() }
            val bytes = if (isConverted(name, originalBytes)) {
                announce(jar)
                JavaxPersistenceTransformer.transform(originalBytes, name, hibernateClassLoader, ::isBundled).also { _convertedClassNames += name }
            } else {
                originalBytes
            }
            defineOrVerifyPackage(name, jar)
            // The signers of a jar entry are only known once the entry has been read in full, which it has been above.
            return defineClass(name, bytes, 0, bytes.size, CodeSource(jar.url, entry.codeSigners))
        }
        return super.findClass(name)
    }

    /**
     * Converts, without defining, every class of every jar that is built for `javax.persistence` alone, so that every class that cannot
     * be converted is found now, whether or not anything loads it later: a CorDapp that would fail in a flow fails the node's start-up
     * instead, with a message that names each such class and what is wrong with it.
     *
     * The classes are read without signature verification: nothing is defined from these bytes, and verifying here would make a jar
     * with a bad signature fail at this point rather than when one of its classes is loaded, as it does in a [URLClassLoader].
     *
     * @throws CordappConversionException listing every class that cannot be converted.
     */
    fun checkConvertible() {
        val failures = ArrayList<String>()
        jars.forEach { checkJar(it, failures) }
        if (failures.isNotEmpty()) {
            throw CordappConversionException("${failures.size} class(es) of the CorDapps built for Corda 4.14 or earlier cannot be converted " +
                    "for Hibernate 7 (Corda 4.15):\n" + failures.joinToString("\n") { "  - $it" } + "\nThe CorDapps that contain them need " +
                    "to be rebuilt for Corda 4.15 - see the \"Upgrading to Hibernate 7\" documentation.")
        }
    }

    private fun checkJar(jar: Jar, failures: MutableList<String>) {
        JarFile(jar.path.toFile(), false, ZipFile.OPEN_READ, JarFile.runtimeVersion()).use { unverified ->
            for (listed in unverified.entries()) {
                val name = classNameOf(listed.name) ?: continue
                // Looked up by name so that a multi-release jar's versioned entry is read, as it is when the class is loaded.
                val entry = unverified.getJarEntry(listed.name) ?: listed
                val bytes = unverified.getInputStream(entry).use { it.readBytes() }
                if (!isConverted(name, bytes)) continue
                announce(jar)
                conversionFailure(jar, name, bytes)?.let { failures += it }
            }
        }
    }

    /** The name of the class in a jar entry, or null for an entry that is not a class to check (a resource, a versioned copy, module-info). */
    private fun classNameOf(entryName: String): String? {
        if (!entryName.endsWith(".class") || entryName.startsWith("META-INF/") || entryName == "module-info.class") return null
        return entryName.removeSuffix(".class").replace('/', '.')
    }

    /** Why the class cannot be converted, or null if it can. */
    private fun conversionFailure(jar: Jar, name: String, bytes: ByteArray): String? {
        return try {
            JavaxPersistenceTransformer.transform(bytes, name, hibernateClassLoader, ::isBundled)
            null
        } catch (e: JavaxPersistenceConversionError) {
            "${jar.path}: ${e.message?.removeSuffix(" ${JavaxPersistenceTransformer.REBUILD_ADVICE}")}"
        }
    }

    private fun isConverted(name: String, classBytes: ByteArray): Boolean {
        return !name.startsWith(JAVAX_PERSISTENCE_PACKAGE) && JavaxPersistenceTransformer.isBuiltForJavaxPersistenceOnly(classBytes)
    }

    /**
     * Whether a class (by internal name) comes with the CorDapps: a Hibernate library a CorDapp ships itself is not the node's to check.
     * Only consulted for a class the node does not have, as the node's copy is the one that runs otherwise (parent-first delegation).
     */
    private fun isBundled(internalName: String): Boolean = findResource("$internalName.class") != null

    private fun announce(jar: Jar) {
        if (announcedJars.add(jar.path)) {
            logger.info("CorDapp ${jar.path} was built for Corda 4.14 or earlier (javax.persistence); its classes are converted " +
                    "to jakarta.persistence for Hibernate 7 as they are loaded")
        }
    }

    /**
     * Defines the package of [className] from the manifest of [jar], as [URLClassLoader] does, or, if it is already defined, verifies
     * sealing the way [URLClassLoader] does: a sealed package accepts classes from its own jar only, and a package first defined unsealed
     * cannot be sealed by a later jar.
     */
    private fun defineOrVerifyPackage(className: String, jar: Jar) {
        val packageName = className.substringBeforeLast('.', "")
        if (packageName.isEmpty()) return
        val manifest = jar.file.manifest
        val existing = getDefinedPackage(packageName)
        if (existing != null) {
            verifySealing(existing, manifest, jar)
            return
        }
        try {
            if (manifest != null) definePackage(packageName, manifest, jar.url) else definePackage(packageName, null, null, null, null, null, null, null)
        } catch (e: IllegalArgumentException) {
            // Defined concurrently by another thread; verify against what it defined.
            verifySealing(getDefinedPackage(packageName) ?: throw e, manifest, jar)
        }
    }

    private fun verifySealing(pkg: Package, manifest: java.util.jar.Manifest?, jar: Jar) {
        if (pkg.isSealed) {
            if (!pkg.isSealed(jar.url)) throw SecurityException("sealing violation: package ${pkg.name} is sealed")
        } else if (manifest != null && isSealed(pkg.name, manifest)) {
            throw SecurityException("sealing violation: can't seal package ${pkg.name}: already loaded")
        }
    }

    /** Whether [manifest] seals [packageName]: its own section, or else the main attributes, says `Sealed: true`. */
    private fun isSealed(packageName: String, manifest: java.util.jar.Manifest): Boolean {
        val sealed = manifest.getAttributes(packageName.replace('.', '/') + "/")?.getValue(Attributes.Name.SEALED)
                ?: manifest.mainAttributes.getValue(Attributes.Name.SEALED)
        return "true".equals(sealed, ignoreCase = true)
    }

    override fun close() {
        try {
            jars.forEach { it.closeIfOpen() }
        } finally {
            super.close()
        }
    }
}
