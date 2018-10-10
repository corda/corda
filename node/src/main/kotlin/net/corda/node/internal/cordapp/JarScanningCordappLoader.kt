package net.corda.node.internal.cordapp

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.*
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.CordappInfoResolver
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.VersionInfo
import net.corda.node.cordapp.CordappLoader
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.nodeapi.internal.coreContractClasses
import net.corda.serialization.internal.DefaultWhitelist
import org.apache.commons.collections4.map.LRUMap
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*
import java.util.jar.JarInputStream
import kotlin.reflect.KClass
import kotlin.streams.toList

/**
 * Handles CorDapp loading and classpath scanning of CorDapp JARs
 *
 * @property cordappJarPaths The classpath of cordapp JARs
 */
class JarScanningCordappLoader private constructor(private val cordappJarPaths: List<RestrictedURL>,
                                                   private val versionInfo: VersionInfo = VersionInfo.UNKNOWN,
                                                   extraCordapps: List<CordappImpl>) : CordappLoaderTemplate() {

    override val cordapps: List<CordappImpl> by lazy {
        loadCordapps() + extraCordapps
    }

    override val appClassLoader: ClassLoader = URLClassLoader(cordappJarPaths.stream().map { it.url }.toTypedArray(), javaClass.classLoader)

    init {
        if (cordappJarPaths.isEmpty()) {
            logger.info("No CorDapp paths provided")
        } else {
            logger.info("Loading CorDapps from ${cordappJarPaths.joinToString()}")
        }
    }

    companion object {
        private val logger = contextLogger()

        /**
         * Creates a CordappLoader from multiple directories.
         *
         * @param corDappDirectories Directories used to scan for CorDapp JARs.
         */
        fun fromDirectories(corDappDirectories: Iterable<Path>, versionInfo: VersionInfo = VersionInfo.UNKNOWN, extraCordapps: List<CordappImpl> = emptyList()): JarScanningCordappLoader {
            logger.info("Looking for CorDapps in ${corDappDirectories.distinct().joinToString(", ", "[", "]")}")
            val paths = corDappDirectories.distinct().flatMap(this::jarUrlsInDirectory).map { it.restricted() }
            return JarScanningCordappLoader(paths, versionInfo, extraCordapps)
        }

        /**
         * Creates a CordappLoader loader out of a list of JAR URLs.
         *
         * @param scanJars Uses the JAR URLs provided for classpath scanning and Cordapp detection.
         */
        fun fromJarUrls(scanJars: List<URL>, versionInfo: VersionInfo = VersionInfo.UNKNOWN, extraCordapps: List<CordappImpl> = emptyList()): JarScanningCordappLoader {
            val paths = scanJars.map { it.restricted() }
            return JarScanningCordappLoader(paths, versionInfo, extraCordapps)
        }

        private fun URL.restricted(rootPackageName: String? = null) = RestrictedURL(this, rootPackageName)

        private fun jarUrlsInDirectory(directory: Path): List<URL> {

            return if (!directory.exists()) {
                emptyList()
            } else {
                directory.list { paths ->
                    // `toFile()` can't be used here...
                    paths.filter { it.toString().endsWith(".jar") }.map { it.toUri().toURL() }.toList()
                }
            }
        }
    }
    private fun loadCordapps(): List<CordappImpl> {
        val cordapps = cordappJarPaths
                .map { scanCordapp(it).toCordapp(it) }
                .filter {
                    if (it.info.minimumPlatformVersion > versionInfo.platformVersion) {
                        logger.warn("Not loading CorDapp ${it.info.shortName} (${it.info.vendor}) as it requires minimum " +
                                "platform version ${it.info.minimumPlatformVersion} (This node is running version ${versionInfo.platformVersion}).")
                        false
                    } else {
                        true
                    }
                }
        cordapps.forEach { CordappInfoResolver.register(it.cordappClasses, it.info) }
        return cordapps
    }
    private fun RestrictedScanResult.toCordapp(url: RestrictedURL): CordappImpl {
        val info = url.url.openStream().let(::JarInputStream).use { it.manifest?.toCordappInfo(CordappImpl.jarName(url.url)) ?: CordappImpl.Info.UNKNOWN }
        return CordappImpl(
                findContractClassNames(this),
                findInitiatedFlows(this),
                findRPCFlows(this),
                findServiceFlows(this),
                findSchedulableFlows(this),
                findServices(this),
                findPlugins(url),
                findSerializers(this),
                findCustomSchemas(this),
                findAllFlows(this),
                url.url,
                info,
                getJarHash(url.url),
                findNotaryService(this)
        )
    }

    private fun findNotaryService(scanResult: RestrictedScanResult): Class<out NotaryService>? {
        // Note: we search for implementations of both NotaryService and TrustedAuthorityNotaryService as
        // the scanner won't find subclasses deeper down the hierarchy if any intermediate class is not
        // present in the CorDapp.
        val result = scanResult.getClassesWithSuperclass(NotaryService::class) +
                scanResult.getClassesWithSuperclass(TrustedAuthorityNotaryService::class)
        logger.info("Found notary service CorDapp implementations: " + result.joinToString(", "))
        return result.firstOrNull()
    }

    private fun getJarHash(url: URL): SecureHash.SHA256 = url.openStream().readFully().sha256()

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

    private fun findAllFlows(scanResult: RestrictedScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getConcreteClassesOfType(FlowLogic::class)
    }

    private fun findContractClassNames(scanResult: RestrictedScanResult): List<String> {
        return coreContractClasses.flatMap { scanResult.getNamesOfClassesImplementing(it) }.distinct()
    }

    private fun findPlugins(cordappJarPath: RestrictedURL): List<SerializationWhitelist> {
        return ServiceLoader.load(SerializationWhitelist::class.java, URLClassLoader(arrayOf(cordappJarPath.url), appClassLoader)).toList().filter {
            it.javaClass.location == cordappJarPath.url && it.javaClass.name.startsWith(cordappJarPath.qualifiedNamePrefix)
        } + DefaultWhitelist // Always add the DefaultWhitelist to the whitelist for an app.
    }

    private fun findSerializers(scanResult: RestrictedScanResult): List<SerializationCustomSerializer<*, *>> {
        return scanResult.getClassesImplementing(SerializationCustomSerializer::class)
    }

    private fun findCustomSchemas(scanResult: RestrictedScanResult): Set<MappedSchema> {
        return scanResult.getClassesWithSuperclass(MappedSchema::class).instances().toSet()
    }

    private val cachedScanResult = LRUMap<RestrictedURL, RestrictedScanResult>(1000)

    private fun scanCordapp(cordappJarPath: RestrictedURL): RestrictedScanResult {
        logger.info("Scanning CorDapp in ${cordappJarPath.url}")
        return cachedScanResult.computeIfAbsent(cordappJarPath) {
            RestrictedScanResult(FastClasspathScanner().addClassLoader(appClassLoader).overrideClasspath(cordappJarPath.url).scan(), cordappJarPath.qualifiedNamePrefix)
        }
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

    /** @property rootPackageName only this package and subpackages may be extracted from [url], or null to allow all packages. */
    private data class RestrictedURL(val url: URL, val rootPackageName: String?) {
        val qualifiedNamePrefix: String get() = rootPackageName?.let { "$it." } ?: ""
    }

    private fun <T : Any> List<Class<out T>>.instances(): List<T> {
        return map { it.kotlin.objectOrNewInstance() }
    }

    private inner class RestrictedScanResult(private val scanResult: ScanResult, private val qualifiedNamePrefix: String) {
        fun getNamesOfClassesImplementing(type: KClass<*>): List<String> {
            return scanResult.getNamesOfClassesImplementing(type.java)
                    .filter { it.startsWith(qualifiedNamePrefix) }
        }

        fun <T : Any> getClassesWithSuperclass(type: KClass<T>): List<Class<out T>> {
            return scanResult.getNamesOfSubclassesOf(type.java)
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { Modifier.isAbstract(it.modifiers) }
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

        fun <T : Any> getConcreteClassesOfType(type: KClass<T>): List<Class<out T>> {
            return scanResult.getNamesOfSubclassesOf(type.java)
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { Modifier.isAbstract(it.modifiers) }
        }
    }
}

/**
 * Thrown when scanning CorDapps.
 */
class MultipleCordappsForFlowException(message: String) : Exception(message)

abstract class CordappLoaderTemplate : CordappLoader {
    override val flowCordappMap: Map<Class<out FlowLogic<*>>, Cordapp> by lazy {
        cordapps.flatMap { corDapp -> corDapp.allFlows.map { flow -> flow to corDapp } }
                .groupBy { it.first }
                .mapValues { entry ->
                    if (entry.value.size > 1) {
                        throw MultipleCordappsForFlowException("There are multiple CorDapp JARs on the classpath for flow " +
                                "${entry.value.first().first.name}: [ ${entry.value.joinToString { it.second.name }} ].")
                    }
                    entry.value.single().second
                }
    }

    override val cordappSchemas: Set<MappedSchema> by lazy {
        cordapps.flatMap { it.customSchemas }.toSet()
    }

    override val appClassLoader: ClassLoader by lazy {
        URLClassLoader(cordapps.stream().map { it.jarPath }.toTypedArray(), javaClass.classLoader)
    }
}
