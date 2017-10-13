package net.corda.node.internal.cordapp

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UpgradedContract
import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.*
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.serialization.DefaultWhitelist
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Modifier
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.reflect.KClass
import kotlin.streams.toList

/**
 * Handles CorDapp loading and classpath scanning of CorDapp JARs
 *
 * @property cordappJarPaths The classpath of cordapp JARs
 */
class CordappLoader private constructor(private val cordappJarPaths: List<URL>) {
    val cordapps: List<Cordapp> by lazy { loadCordapps() + coreCordapp }

    internal val appClassLoader: ClassLoader = URLClassLoader(cordappJarPaths.toTypedArray(), javaClass.classLoader)

    init {
        if (cordappJarPaths.isEmpty()) {
            logger.info("No CorDapp paths provided")
        } else {
            logger.info("Loading CorDapps from ${cordappJarPaths.joinToString()}")
        }
    }

    companion object {
        private val logger = loggerFor<CordappLoader>()

        /**
         * Default cordapp dir name
         */
        val CORDAPPS_DIR_NAME = "cordapps"

        /**
         * Creates a default CordappLoader intended to be used in non-dev or non-test environments.
         *
         * @param baseDir The directory that this node is running in. Will use this to resolve the cordapps directory
         *                  for classpath scanning.
         */
        fun createDefault(baseDir: Path) = CordappLoader(getCordappsInDirectory(getCordappsPath(baseDir)))

        /**
         * Create a dev mode CordappLoader for test environments that creates and loads cordapps from the classpath
         * and cordapps directory. This is intended mostly for use by the driver.
         *
         * @param baseDir See [createDefault.baseDir]
         * @param testPackages See [createWithTestPackages.testPackages]
         */
        @VisibleForTesting
        fun createDefaultWithTestPackages(configuration: NodeConfiguration, testPackages: List<String>): CordappLoader {
            check(configuration.devMode) { "Package scanning can only occur in dev mode" }
            return CordappLoader(getCordappsInDirectory(getCordappsPath(configuration.baseDirectory)) + testPackages.flatMap(this::createScanPackage))
        }

        /**
         * Create a dev mode CordappLoader for test environments that creates and loads cordapps from the classpath.
         * This is intended for use in unit and integration tests.
         *
         * @param testPackages List of package names that contain CorDapp classes that can be automatically turned into
         * CorDapps.
         */
        @VisibleForTesting
        fun createWithTestPackages(testPackages: List<String>)
                = CordappLoader(testPackages.flatMap(this::createScanPackage))

        /**
         * Creates a dev mode CordappLoader intended only to be used in test environments
         *
         * @param scanJars Uses the JAR URLs provided for classpath scanning and Cordapp detection
         */
        @VisibleForTesting
        fun createDevMode(scanJars: List<URL>) = CordappLoader(scanJars)

        private fun getCordappsPath(baseDir: Path): Path = baseDir / CORDAPPS_DIR_NAME

        private fun createScanPackage(scanPackage: String): List<URL> {
            val resource = scanPackage.replace('.', '/')
            return this::class.java.classLoader.getResources(resource)
                    .asSequence()
                    .map { path ->
                        if (path.protocol == "jar") {
                            (path.openConnection() as JarURLConnection).jarFileURL.toURI()
                        } else {
                            createDevCordappJar(scanPackage, path, resource)
                        }.toURL()
                    }
                    .toList()
        }

        /** Takes a package of classes and creates a JAR from them - only use in tests. */
        private fun createDevCordappJar(scanPackage: String, path: URL, jarPackageName: String): URI {
            if (!generatedCordapps.contains(path)) {
                val cordappDir = File("build/tmp/generated-test-cordapps")
                cordappDir.mkdirs()
                val cordappJAR = File(cordappDir, "$scanPackage-${UUID.randomUUID()}.jar")
                logger.info("Generating a test-only cordapp of classes discovered in $scanPackage at $cordappJAR")
                FileOutputStream(cordappJAR).use {
                    JarOutputStream(it).use { jos ->
                        val scanDir = File(path.toURI())
                        scanDir.walkTopDown().forEach {
                            val entryPath = jarPackageName + "/" + scanDir.toPath().relativize(it.toPath()).toString().replace('\\', '/')
                            val time = FileTime.from(Instant.EPOCH)
                            val entry = ZipEntry(entryPath).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
                            jos.putNextEntry(entry)
                            if (it.isFile) {
                                Files.copy(it.toPath(), jos)
                            }
                            jos.closeEntry()
                        }
                    }
                }
                generatedCordapps[path] = cordappJAR.toURI()
            }

            return generatedCordapps[path]!!
        }

        private fun getCordappsInDirectory(cordappsDir: Path): List<URL> {
            return if (!cordappsDir.exists()) {
                emptyList<URL>()
            } else {
                cordappsDir.list {
                    it.filter { it.isRegularFile() && it.toString().endsWith(".jar") }.map { it.toUri().toURL() }.toList()
                }
            }
        }

        private val generatedCordapps = mutableMapOf<URL, URI>()

        /** A list of the core RPC flows present in Corda */
        private val coreRPCFlows = listOf(
                ContractUpgradeFlow.Initiate::class.java,
                ContractUpgradeFlow.Authorise::class.java,
                ContractUpgradeFlow.Deauthorise::class.java)

        /** A Cordapp representing the core package which is not scanned automatically. */
        @VisibleForTesting
        internal val coreCordapp = CordappImpl(
                listOf(),
                listOf(),
                coreRPCFlows,
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                setOf(),
                ContractUpgradeFlow.javaClass.protectionDomain.codeSource.location // Core JAR location
        )
    }

    private fun loadCordapps(): List<Cordapp> {
        return cordappJarPaths.map {
            val scanResult = scanCordapp(it)
            CordappImpl(findContractClassNames(scanResult),
                    findInitiatedFlows(scanResult),
                    findRPCFlows(scanResult),
                    findServiceFlows(scanResult),
                    findSchedulableFlows(scanResult),
                    findServices(scanResult),
                    findPlugins(it),
                    findCustomSchemas(scanResult),
                    it)
        }
    }

    private fun findServices(scanResult: ScanResult): List<Class<out SerializeAsToken>> {
        return scanResult.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
    }

    private fun findInitiatedFlows(scanResult: ScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, InitiatedBy::class)
                // First group by the initiating flow class in case there are multiple mappings
                .groupBy { it.requireAnnotation<InitiatedBy>().value.java }
                .map { (initiatingFlow, initiatedFlows) ->
                    val sorted = initiatedFlows.sortedWith(FlowTypeHierarchyComparator(initiatingFlow))
                    if (sorted.size > 1) {
                        logger.warn("${initiatingFlow.name} has been specified as the inititating flow by multiple flows " +
                                "in the same type hierarchy: ${sorted.joinToString { it.name }}. Choosing the most " +
                                "specific sub-type for registration: ${sorted[0].name}.")
                    }
                    sorted[0]
                }
    }

    private fun Class<out FlowLogic<*>>.isUserInvokable(): Boolean {
        return Modifier.isPublic(modifiers) && !isLocalClass && !isAnonymousClass && (!isMemberClass || Modifier.isStatic(modifiers))
    }

    private fun findRPCFlows(scanResult: ScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, StartableByRPC::class).filter { it.isUserInvokable() }
    }

    private fun findServiceFlows(scanResult: ScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, StartableByService::class)
    }

    private fun findSchedulableFlows(scanResult: ScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, SchedulableFlow::class)
    }

    private fun findContractClassNames(scanResult: ScanResult): List<String> {
        return (scanResult.getNamesOfClassesImplementing(Contract::class.java) + scanResult.getNamesOfClassesImplementing(UpgradedContract::class.java)).distinct()
    }

    private fun findPlugins(cordappJarPath: URL): List<SerializationWhitelist> {
        return ServiceLoader.load(SerializationWhitelist::class.java, URLClassLoader(arrayOf(cordappJarPath), appClassLoader)).toList().filter {
            cordappJarPath == it.javaClass.protectionDomain.codeSource.location
        } + DefaultWhitelist // Always add the DefaultWhitelist to the whitelist for an app.
    }

    private fun findCustomSchemas(scanResult: ScanResult): Set<MappedSchema> {
        return scanResult.getClassesWithSuperclass(MappedSchema::class).toSet()
    }

    private fun scanCordapp(cordappJarPath: URL): ScanResult {
        logger.info("Scanning CorDapp in $cordappJarPath")
        return FastClasspathScanner().addClassLoader(appClassLoader).overrideClasspath(cordappJarPath).scan()
    }

    private class FlowTypeHierarchyComparator(val initiatingFlow: Class<out FlowLogic<*>>) : Comparator<Class<out FlowLogic<*>>> {
        override fun compare(o1: Class<out FlowLogic<*>>, o2: Class<out FlowLogic<*>>): Int {
            return when {
                o1 == o2 -> 0
                o1.isAssignableFrom(o2) -> 1
                o2.isAssignableFrom(o1) -> -1
                else -> throw IllegalArgumentException("${initiatingFlow.name} has been specified as the initiating flow by " +
                        "both ${o1.name} and ${o2.name}")
            }
        }
    }

    private fun <T : Any> loadClass(className: String, type: KClass<T>): Class<out T>? {
        return try {
            appClassLoader.loadClass(className).asSubclass(type.java)
        } catch (e: ClassCastException) {
            logger.warn("As $className must be a sub-type of ${type.java.name}")
            null
        } catch (e: Exception) {
            logger.warn("Unable to load class $className", e)
            null
        }
    }

    private fun <T : Any> ScanResult.getClassesWithSuperclass(type: KClass<T>): List<T> {
        return getNamesOfSubclassesOf(type.java)
                .mapNotNull { loadClass(it, type) }
                .filterNot { Modifier.isAbstract(it.modifiers) }
                .map { it.kotlin.objectOrNewInstance() }
    }

    private fun <T : Any> ScanResult.getClassesWithAnnotation(type: KClass<T>, annotation: KClass<out Annotation>): List<Class<out T>> {
        return getNamesOfClassesWithAnnotation(annotation.java)
                .mapNotNull { loadClass(it, type) }
                .filterNot { Modifier.isAbstract(it.modifiers) }
    }
}
