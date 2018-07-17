package net.corda.node.internal.cordapp

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
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
import net.corda.nodeapi.internal.coreContractClasses
import net.corda.serialization.internal.DefaultWhitelist
import org.apache.commons.collections4.map.LRUMap
import java.io.File
import java.lang.reflect.Modifier
import java.net.JarURLConnection
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarOutputStream
import java.util.stream.Collectors
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

    // Create a map of the CorDapps that provide a Flow. If a flow is not in this map it is a Core flow.
    // It also checks that there is only one CorDapp containing that flow class
    val flowCordappMap: Map<Class<out FlowLogic<*>>, Cordapp> by lazy {
        cordapps.flatMap { corDapp -> corDapp.allFlows.map { flow -> flow to corDapp } }
                .groupBy { it.first }
                .mapValues {
                    if(it.value.size > 1) { throw MultipleCordappsForFlowException("There are multiple CorDapp JARs on the classpath for flow ${it.value.first().first.name}: [ ${it.value.joinToString { it.second.name }} ].")  }
                    it.value.single().second
                }
    }

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
         * Creates a CordappLoader from multiple directories.
         *
         * @param corDappDirectories Directories used to scan for CorDapp JARs.
         */
        fun fromDirectories(corDappDirectories: Iterable<Path>): CordappLoader {

            logger.info("Looking for CorDapps in ${corDappDirectories.distinct().joinToString(File.pathSeparator, "[", "]")}")
            return CordappLoader(corDappDirectories.distinct().flatMap(this::jarUrlsInDirectory).map { it.restricted() })
        }

        /**
         * Creates a CordappLoader loader out of a list of JAR URLs.
         *
         * @param scanJars Uses the JAR URLs provided for classpath scanning and Cordapp detection.
         */
        fun fromJarUrls(scanJars: List<URL>) = CordappLoader(scanJars.map { it.restricted() })

        /**
         * Create a dev mode CordappLoader for test environments that creates and loads cordapps from the classpath.
         * This is intended for use in unit and integration tests.
         *
         * @param testPackages List of package names that contain CorDapp classes that can be automatically turned into
         * CorDapps.
         */
        // TODO sollecitom remove
        @VisibleForTesting
        fun createWithTestPackages(testPackages: List<String>): CordappLoader {
            val urls = simplifyScanPackages(testPackages).flatMap(this::getPackageURLs)
            return cordappLoadersCache.asMap().computeIfAbsent(urls, ::CordappLoader)
        }

        // Cache for CordappLoaders to avoid costly classpath scanning
        private val cordappLoadersCache = Caffeine.newBuilder().softValues().build<List<RestrictedURL>, CordappLoader>()
        private val generatedCordapps = ConcurrentHashMap<URL, Path>()

        private fun simplifyScanPackages(scanPackages: List<String>): List<String> {
            return scanPackages.sorted().fold(emptyList()) { listSoFar, packageName ->
                when {
                    listSoFar.isEmpty() -> listOf(packageName)
                    packageName.startsWith(listSoFar.last()) -> listSoFar  // Squash ["com.foo", "com.foo.bar"] into just ["com.foo"]
                    else -> listSoFar + packageName
                }
            }
        }

        private fun URL.restricted(rootPackageName: String? = null) =  RestrictedURL(this, rootPackageName)

        private fun getPackageURLs(scanPackage: String): List<RestrictedURL> {
            val resource = scanPackage.replace('.', '/')
            return this::class.java.classLoader.getResources(resource)
                    .asSequence()
                    // This is to only scan classes from test folders.
                    .filter { url ->
                         !url.toString().contains("main/$resource")  || listOf("net.corda.core", "net.corda.node", "net.corda.finance").none { scanPackage.startsWith(it) }
                    }
                    .map { url ->
                        if (url.protocol == "jar") {
                            // When running tests from gradle this may be a corda module jar, so restrict to scanPackage:
                            RestrictedURL((url.openConnection() as JarURLConnection).jarFileURL, scanPackage)
                        } else {
                            // No need to restrict as createDevCordappJar has already done that:
                            RestrictedURL(createDevCordappJar(scanPackage, url, resource).toUri().toURL(), null)
                        }
                    }
                    .toList()
        }

        /** Takes a package of classes and creates a JAR from them - only use in tests. */
        private fun createDevCordappJar(scanPackage: String, url: URL, resource: String): Path {
            return generatedCordapps.computeIfAbsent(url) {
                // TODO Using the driver in out-of-process mode causes each node to have their own copy of the same dev CorDapps
                val cordappDir = (Paths.get("build") / "tmp" / "generated-test-cordapps").createDirectories()
                val cordappJar = cordappDir / "$scanPackage-${UUID.randomUUID()}.jar"
                logger.info("Generating a test-only CorDapp of classes discovered for package $scanPackage in $url: $cordappJar")
                JarOutputStream(cordappJar.outputStream()).use { jos ->
                    val scanDir = url.toPath()
                    scanDir.walk {
                        it.forEach {
                            val entryPath = "$resource/${scanDir.relativize(it).toString().replace('\\', '/')}"
                            val time = FileTime.from(Instant.EPOCH)
                            val entry = ZipEntry(entryPath).setCreationTime(time).setLastAccessTime(time).setLastModifiedTime(time)
                            jos.putNextEntry(entry)
                            if (it.isRegularFile()) {
                                it.copyTo(jos)
                            }
                            jos.closeEntry()
                        }
                    }
                }
                cordappJar
            }
        }

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
                allFlows = listOf(),
                jarPath = ContractUpgradeFlow.javaClass.location, // Core JAR location
                jarHash = SecureHash.allOnesHash
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
                    findAllFlows(scanResult),
                    it.url,
                    getJarHash(it.url)
            )
        }
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
        return scanResult.getClassesWithSuperclass(MappedSchema::class).toSet()
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
