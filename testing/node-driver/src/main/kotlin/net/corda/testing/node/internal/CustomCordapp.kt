package net.corda.testing.node.internal

import io.github.classgraph.ClassGraph
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.set
import net.corda.core.internal.pooledScan
import net.corda.core.node.services.AttachmentFixup
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.outputStream

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
        val fixups: List<AttachmentFixup> = emptyList(),
        val signingInfo: SigningInfo? = null,
        override val config: Map<String, Any> = emptyMap()
) : TestCordappInternal() {

    override val jarFile: Path get() = getJarFile(this)

    override fun withConfig(config: Map<String, Any>): CustomCordapp = copy(config = config)

    override fun withOnlyJarContents(): CustomCordapp = CustomCordapp(packages = packages, classes = classes, fixups = fixups)

    override fun asSigned(): CustomCordapp = signed()

    fun signed(keyStorePath: Path? = null, numberOfSignatures: Int = 1, keyAlgorithm: String = "RSA"): CustomCordapp =
            copy(signingInfo = SigningInfo(keyStorePath, numberOfSignatures, keyAlgorithm))

    @VisibleForTesting
    internal fun packageAsJar(file: Path) {
        val classGraph = ClassGraph()
        if (packages.isNotEmpty()) {
            classGraph.acceptPaths(*packages.map { it.replace('.', '/') }.toTypedArray())
        }
        if (classes.isNotEmpty()) {
            classes.forEach { classGraph.addClassLoader(it.classLoader) }
            classGraph.acceptClasses(*classes.map { it.name }.toTypedArray())
        }

        classGraph.enableClassInfo().pooledScan().use { scanResult ->
            if (scanResult.allResources.isEmpty()) {
                throw ClassNotFoundException(
                    "Could not create jar file as the given classes(${classes.joinToString()}) / packages(${packages.joinToString()}) were not found on the classpath"
                )
            }

            val whitelistService = SerializationWhitelist::class.java.name
            val whitelists = scanResult.getClassesImplementing(whitelistService)

            JarOutputStream(file.outputStream()).use { jos ->
                jos.addEntry(testEntry(JarFile.MANIFEST_NAME)) {
                    createTestManifest(name, versionId, targetPlatformVersion).write(jos)
                }
                if (whitelists.isNotEmpty()) {
                    jos.addEntry(directoryEntry("META-INF/services"))
                    jos.addEntry(testEntry("META-INF/services/$whitelistService")) {
                        jos.write(whitelists.names.joinToString(separator = "\r\n").toByteArray())
                    }
                }

                // The same resource may be found in different locations (this will happen when running from gradle) so just
                // pick the first one found.
                scanResult.allResourcesAsMap.forEach { (path, resourceList) ->
                    jos.addEntry(testEntry(path), resourceList[0].open())
                }
            }
        }
    }

    internal fun createFixupJar(file: Path) {
        JarOutputStream(file.outputStream()).use { jos ->
            jos.addEntry(testEntry(JarFile.MANIFEST_NAME)) {
                createTestManifest(name, versionId, targetPlatformVersion).write(jos)
            }
            jos.addEntry(testEntry("META-INF/Corda-Fixups")) {
                fixups.filter { it.first.isNotEmpty() }.forEach { (source, target) ->
                    val data = source.joinToString(
                        separator = ",",
                        postfix = target.joinToString(
                            separator = ",",
                            prefix = "=>",
                            postfix = "\r\n"
                        )
                    )
                    jos.write(data.toByteArray())
                }
            }
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

    private fun directoryEntry(name: String): ZipEntry {
        val directoryName = if (name.endsWith('/')) name else "$name/"
        return testEntry(directoryName).apply {
            method = ZipEntry.STORED
            compressedSize = 0
            size = 0
            crc = 0
        }
    }

    data class SigningInfo(val keyStorePath: Path?, val signatureCount: Int, val algorithm: String)

    companion object {
        private val logger = contextLogger()
        private val epochFileTime = FileTime.from(Instant.EPOCH)
        private val cordappsDirectory: Path
        private val whitespace = "\\s++".toRegex()
        private val cache = ConcurrentHashMap<CustomCordapp, Path>()

        init {
            val buildDir = Paths.get("build").toAbsolutePath()
            val timeDirName = getTimestampAsDirectoryName()
            cordappsDirectory = buildDir / "generated-custom-cordapps" / timeDirName
        }

        fun getJarFile(cordapp: CustomCordapp): Path {
            // The CorDapp config is external to the jar and so can be ignored here
            return cache.computeIfAbsent(cordapp.copy(config = emptyMap())) {
                val filename = it.run { "${name.replace(whitespace, "-")}_${versionId}_${targetPlatformVersion}_${UUID.randomUUID()}.jar" }
                val jarFile = cordappsDirectory.createDirectories() / filename
                if (it.fixups.isNotEmpty()) {
                    it.createFixupJar(jarFile)
                } else if (it.packages.isNotEmpty() || it.classes.isNotEmpty()) {
                    it.packageAsJar(jarFile)
                }
                if (it.signingInfo != null) {
                    TestCordappSigner.signJar(jarFile, it.signingInfo.keyStorePath, it.signingInfo.signatureCount, it.signingInfo.algorithm)
                }
                logger.debug { "$it packaged into $jarFile" }
                jarFile
            }
        }
    }
}
