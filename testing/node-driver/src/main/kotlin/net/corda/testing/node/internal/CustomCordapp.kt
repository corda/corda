package net.corda.testing.node.internal

import io.github.classgraph.ClassGraph
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.set
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

/**
 * Represents a completely custom CorDapp comprising of resources taken from packages on the existing classpath, even including individual
 * disparate classes. The CorDapp metadata that's present in the MANIFEST can also be tailored.
 */
data class CustomCordapp(
        val packages: Set<String> = emptySet(),
        val name: String = "custom-cordapp",
        val versionId: Int = 1,
        val targetPlatformVersion: Int = PLATFORM_VERSION,
        val classes: Set<Class<*>> = emptySet(),
        val signingInfo: SigningInfo? = null,
        override val config: Map<String, Any> = emptyMap()
) : TestCordappInternal() {
    init {
        require(packages.isNotEmpty() || classes.isNotEmpty()) { "At least one package or class must be specified" }
    }

    override val jarFile: Path get() = getJarFile(this)

    override fun withConfig(config: Map<String, Any>): CustomCordapp = copy(config = config)

    override fun withOnlyJarContents(): CustomCordapp = CustomCordapp(packages = packages, classes = classes)

    fun signed(keyStorePath: Path? = null): CustomCordapp = copy(signingInfo = SigningInfo(keyStorePath))

    @VisibleForTesting
    internal fun packageAsJar(file: Path) {
        val classGraph = ClassGraph()
        if (packages.isNotEmpty()) {
            classGraph.whitelistPaths(*packages.map { it.replace('.', '/') }.toTypedArray())
        }
        if (classes.isNotEmpty()) {
            classes.forEach { classGraph.addClassLoader(it.classLoader) }
            classGraph.whitelistClasses(*classes.map { it.name }.toTypedArray())
        }

        classGraph.pooledScan().use { scanResult ->
            JarOutputStream(file.outputStream()).use { jos ->
                jos.addEntry(testEntry(JarFile.MANIFEST_NAME)) {
                    createTestManifest(name, versionId, targetPlatformVersion).write(jos)
                }

                // The same resource may be found in different locations (this will happen when running from gradle) so just
                // pick the first one found.
                scanResult.allResources.asMap().forEach { path, resourceList ->
                    jos.addEntry(testEntry(path), resourceList[0].open())
                }
            }
        }
    }

    private fun signJar(jarFile: Path) {
        if (signingInfo != null) {
            val testKeystore = "_teststore"
            val alias = "Test"
            val pwd = "secret!"
            val keyStorePathToUse = if (signingInfo.keyStorePath != null) {
                signingInfo.keyStorePath
            } else {
                defaultJarSignerDirectory.createDirectories()
                if (!(defaultJarSignerDirectory / testKeystore).exists()) {
                    defaultJarSignerDirectory.generateKey(alias, pwd, "O=Test Company Ltd,OU=Test,L=London,C=GB")
                }
                defaultJarSignerDirectory
            }
            val pk = keyStorePathToUse.signJar(jarFile.toString(), alias, pwd)
            logger.debug { "Signed Jar: $jarFile with public key $pk" }
        } else {
            logger.debug { "Unsigned Jar: $jarFile" }
        }
    }

    private fun createTestManifest(name: String, versionId: Int, targetPlatformVersion: Int): Manifest {
        val manifest = Manifest()

        // Mandatory manifest attribute. If not present, all other entries are silently skipped.
        manifest[Attributes.Name.MANIFEST_VERSION] = "1.0"

        manifest[CordappImpl.CORDAPP_CONTRACT_NAME]  = name
        manifest[CordappImpl.CORDAPP_CONTRACT_VERSION] = versionId.toString()
        manifest[CordappImpl.CORDAPP_WORKFLOW_NAME]  = name
        manifest[CordappImpl.CORDAPP_WORKFLOW_VERSION] = versionId.toString()
        manifest[CordappImpl.TARGET_PLATFORM_VERSION] = targetPlatformVersion.toString()

        return manifest
    }

    private fun testEntry(name: String): ZipEntry {
        return ZipEntry(name).setCreationTime(epochFileTime).setLastAccessTime(epochFileTime).setLastModifiedTime(epochFileTime)
    }

    data class SigningInfo(val keyStorePath: Path? = null)

    companion object {
        private val logger = contextLogger()
        private val epochFileTime = FileTime.from(Instant.EPOCH)
        private val cordappsDirectory: Path
        private val defaultJarSignerDirectory: Path
        private val whitespace = "\\s".toRegex()
        private val cache = ConcurrentHashMap<CustomCordapp, Path>()

        init {
            val buildDir = Paths.get("build").toAbsolutePath()
            val timeDirName = getTimestampAsDirectoryName()
            cordappsDirectory = buildDir / "generated-custom-cordapps" / timeDirName
            defaultJarSignerDirectory = buildDir / "jar-signer" / timeDirName
        }

        fun getJarFile(cordapp: CustomCordapp): Path {
            // The CorDapp config is external to the jar and so can be ignored here
            return cache.computeIfAbsent(cordapp.copy(config = emptyMap())) {
                val filename = it.run { "${name.replace(whitespace, "-")}_${versionId}_${targetPlatformVersion}_${UUID.randomUUID()}.jar" }
                val jarFile = cordappsDirectory.createDirectories() / filename
                it.packageAsJar(jarFile)
                it.signJar(jarFile)
                logger.debug { "$it packaged into $jarFile" }
                jarFile
            }
        }
    }
}
