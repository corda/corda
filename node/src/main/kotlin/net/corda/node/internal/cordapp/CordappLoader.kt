/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.cordapp

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UpgradedContract
import net.corda.core.contracts.UpgradedContractWithLegacyConstraint
import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.*
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.coreContractClasses
import net.corda.nodeapi.internal.serialization.DefaultWhitelist
import org.apache.commons.collections4.map.LRUMap
import java.lang.reflect.Modifier
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
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
class CordappLoader private constructor(private val cordappJarPaths: List<RestrictedURL>) {
    val cordapps: List<Cordapp> by lazy { loadCordapps() + coreCordapp }
    val appClassLoader: ClassLoader = URLClassLoader(cordappJarPaths.stream().map { it.url }.toTypedArray(), javaClass.classLoader)

    init {
        if (cordappJarPaths.isEmpty()) {
            logger.info("No CorDapp paths provided")
        } else {
            logger.info("Loading CorDapps from ${cordappJarPaths.joinToString()}")
        }
    }

    val cordappSchemas: Set<MappedSchema> get() = cordapps.flatMap { it.customSchemas }.toSet()

    companion object {
        private val logger = contextLogger()
        /**
         * Default cordapp dir name
         */
        private const val CORDAPPS_DIR_NAME = "cordapps"

        /**
         * Creates a default CordappLoader intended to be used in non-dev or non-test environments.
         *
         * @param baseDir The directory that this node is running in. Will use this to resolve the cordapps directory
         *                  for classpath scanning.
         */
        fun createDefault(baseDir: Path) = CordappLoader(getCordappsInDirectory(getCordappsPath(baseDir)))

        // Cache for CordappLoaders to avoid costly classpath scanning
        private val cordappLoadersCache = LRUMap<List<*>, CordappLoader>(1000)

        /**
         * Create a dev mode CordappLoader for test environments that creates and loads cordapps from the classpath
         * and cordapps directory. This is intended mostly for use by the driver.
         *
         * @param testPackages See [createWithTestPackages]
         */
        @VisibleForTesting
        fun createDefaultWithTestPackages(configuration: NodeConfiguration, testPackages: List<String>): CordappLoader {
            if (!configuration.devMode) {
                logger.warn("Package scanning should only occur in dev mode!")
            }
            val paths = getCordappsInDirectory(getCordappsPath(configuration.baseDirectory)) + testPackages.flatMap(this::createScanPackage)
            return cordappLoadersCache.computeIfAbsent(paths, { CordappLoader(paths) })
        }

        /**
         * Create a dev mode CordappLoader for test environments that creates and loads cordapps from the classpath.
         * This is intended for use in unit and integration tests.
         *
         * @param testPackages List of package names that contain CorDapp classes that can be automatically turned into
         * CorDapps.
         */
        @VisibleForTesting
        fun createWithTestPackages(testPackages: List<String>): CordappLoader {
            return cordappLoadersCache.computeIfAbsent(testPackages, { CordappLoader(testPackages.flatMap(this::createScanPackage)) })
        }

        /**
         * Creates a dev mode CordappLoader intended only to be used in test environments
         *
         * @param scanJars Uses the JAR URLs provided for classpath scanning and Cordapp detection
         */
        @VisibleForTesting
        fun createDevMode(scanJars: List<URL>) = CordappLoader(scanJars.map { RestrictedURL(it, null) })

        private fun getCordappsPath(baseDir: Path): Path = baseDir / CORDAPPS_DIR_NAME

        private fun createScanPackage(scanPackage: String): List<RestrictedURL> {
            val resource = scanPackage.replace('.', '/')
            return this::class.java.classLoader.getResources(resource)
                    .asSequence()
                    .map { path ->
                        if (path.protocol == "jar") {
                            // When running tests from gradle this may be a corda module jar, so restrict to scanPackage:
                            RestrictedURL((path.openConnection() as JarURLConnection).jarFileURL, scanPackage)
                        } else {
                            // No need to restrict as createDevCordappJar has already done that:
                            RestrictedURL(createDevCordappJar(scanPackage, path, resource).toURL(), null)
                        }
                    }
                    .toList()
        }

        /** Takes a package of classes and creates a JAR from them - only use in tests. */
        private fun createDevCordappJar(scanPackage: String, url: URL, jarPackageName: String): URI {
            return generatedCordapps.computeIfAbsent(url) {
                val cordappDir = (Paths.get("build") / "tmp" / "generated-test-cordapps").createDirectories()
                val cordappJAR = cordappDir / "$scanPackage-${UUID.randomUUID()}.jar"
                logger.info("Generating a test-only cordapp of classes discovered in $scanPackage at $cordappJAR")
                JarOutputStream(cordappJAR.outputStream()).use { jos ->
                    val scanDir = url.toPath()
                    scanDir.walk { it.forEach {
                        val entryPath = "$jarPackageName/${scanDir.relativize(it).toString().replace('\\', '/')}"
                        val time = FileTime.from(Instant.EPOCH)
                        val entry = ZipEntry(entryPath).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
                        jos.putNextEntry(entry)
                        if (it.isRegularFile()) {
                            it.copyTo(jos)
                        }
                        jos.closeEntry()
                    } }
                }
                cordappJAR.toUri()
            }
        }

        private fun getCordappsInDirectory(cordappsDir: Path): List<RestrictedURL> {
            return if (!cordappsDir.exists()) {
                emptyList()
            } else {
                cordappsDir.list {
                    it.filter { it.isRegularFile() && it.toString().endsWith(".jar") }.map { RestrictedURL(it.toUri().toURL(), null) }.toList()
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
                contractClassNames = listOf(),
                initiatedFlows = listOf(),
                rpcFlows = coreRPCFlows,
                serviceFlows = listOf(),
                schedulableFlows = listOf(),
                services = listOf(),
                serializationWhitelists = listOf(),
                serializationCustomSerializers = listOf(),
                customSchemas = setOf(),
                jarPath = ContractUpgradeFlow.javaClass.protectionDomain.codeSource.location // Core JAR location
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
                    findSerializers(scanResult),
                    findCustomSchemas(scanResult),
                    it.url)
        }
    }

    private fun findServices(scanResult: RestrictedScanResult): List<Class<out SerializeAsToken>> {
        return scanResult.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
    }

    private fun findInitiatedFlows(scanResult: RestrictedScanResult): List<Class<out FlowLogic<*>>> {
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

    private fun findRPCFlows(scanResult: RestrictedScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, StartableByRPC::class).filter { it.isUserInvokable() }
    }

    private fun findServiceFlows(scanResult: RestrictedScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, StartableByService::class)
    }

    private fun findSchedulableFlows(scanResult: RestrictedScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, SchedulableFlow::class)
    }

    private fun findContractClassNames(scanResult: RestrictedScanResult): List<String> {
        return coreContractClasses.flatMap { scanResult.getNamesOfClassesImplementing(it) }.distinct()
    }

    private fun findPlugins(cordappJarPath: RestrictedURL): List<SerializationWhitelist> {
        return ServiceLoader.load(SerializationWhitelist::class.java, URLClassLoader(arrayOf(cordappJarPath.url), appClassLoader)).toList().filter {
            it.javaClass.protectionDomain.codeSource.location == cordappJarPath.url && it.javaClass.name.startsWith(cordappJarPath.qualifiedNamePrefix)
        } + DefaultWhitelist // Always add the DefaultWhitelist to the whitelist for an app.
    }

    private fun findSerializers(scanResult: RestrictedScanResult): List<SerializationCustomSerializer<*, *>> {
        return scanResult.getClassesImplementing(SerializationCustomSerializer::class)
    }

    private fun findCustomSchemas(scanResult: RestrictedScanResult): Set<MappedSchema> {
        return scanResult.getClassesWithSuperclass(MappedSchema::class).toSet()
    }

    private val cachedScanResult = LRUMap<RestrictedURL, RestrictedScanResult>(1000)
    private fun scanCordapp(cordappJarPath: RestrictedURL): RestrictedScanResult {
        logger.info("Scanning CorDapp in $cordappJarPath")
        return cachedScanResult.computeIfAbsent(cordappJarPath, {
            RestrictedScanResult(FastClasspathScanner().addClassLoader(appClassLoader).overrideClasspath(cordappJarPath.url).scan(), cordappJarPath.qualifiedNamePrefix)
        })
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

    /** @param rootPackageName only this package and subpackages may be extracted from [url], or null to allow all packages. */
    private class RestrictedURL(val url: URL, rootPackageName: String?) {
        val qualifiedNamePrefix = rootPackageName?.let { it + '.' } ?: ""
        override fun toString() = url.toString()
    }

    private inner class RestrictedScanResult(private val scanResult: ScanResult, private val qualifiedNamePrefix: String) {
        fun getNamesOfClassesImplementing(type: KClass<*>): List<String> {
            return scanResult.getNamesOfClassesImplementing(type.java)
                    .filter { it.startsWith(qualifiedNamePrefix) }
        }

        fun <T : Any> getClassesWithSuperclass(type: KClass<T>): List<T> {
            return scanResult.getNamesOfSubclassesOf(type.java)
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { Modifier.isAbstract(it.modifiers) }
                    .map { it.kotlin.objectOrNewInstance() }
        }

        fun <T : Any> getClassesImplementing(type: KClass<T>): List<T> {
            return scanResult.getNamesOfClassesImplementing(type.java)
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { Modifier.isAbstract(it.modifiers) }
                    .map { it.kotlin.objectOrNewInstance() }
        }

        fun <T : Any> getClassesWithAnnotation(type: KClass<T>, annotation: KClass<out Annotation>): List<Class<out T>> {
            return scanResult.getNamesOfClassesWithAnnotation(annotation.java)
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { Modifier.isAbstract(it.modifiers) }
        }
    }
}
