package net.corda.node.internal.cordapp

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.*
import net.corda.core.internal.*
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.CordappImpl.Companion.UNKNOWN_INFO
import net.corda.core.internal.cordapp.CordappResolver
import net.corda.core.internal.cordapp.get
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.VersionInfo
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.coreContractClasses
import net.corda.serialization.internal.DefaultWhitelist
import org.apache.commons.collections4.map.LRUMap
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.zip.ZipInputStream
import kotlin.reflect.KClass
import kotlin.streams.toList

/**
 * Handles CorDapp loading and classpath scanning of CorDapp JARs
 *
 * @property cordappJarPaths The classpath of cordapp JARs
 */
class JarScanningCordappLoader private constructor(private val cordappJarPaths: List<RestrictedURL>,
                                                   private val versionInfo: VersionInfo = VersionInfo.UNKNOWN,
                                                   extraCordapps: List<CordappImpl>,
                                                   private val signerKeyFingerprintBlacklist: List<SecureHash.SHA256> = emptyList()) : CordappLoaderTemplate() {
    init {
        if (cordappJarPaths.isEmpty()) {
            logger.info("No CorDapp paths provided")
        } else {
            logger.info("Loading CorDapps from ${cordappJarPaths.joinToString()}")
        }
    }

    override val cordapps: List<CordappImpl> by lazy { loadCordapps() + extraCordapps }

    override val appClassLoader: URLClassLoader = URLClassLoader(cordappJarPaths.stream().map { it.url }.toTypedArray(), javaClass.classLoader)

    override fun close() = appClassLoader.close()

    companion object {
        private val logger = contextLogger()

        /**
         * Creates a CordappLoader from multiple directories.
         *
         * @param cordappDirs Directories used to scan for CorDapp JARs.
         */
        fun fromDirectories(cordappDirs: Collection<Path>,
                            versionInfo: VersionInfo = VersionInfo.UNKNOWN,
                            extraCordapps: List<CordappImpl> = emptyList(),
                            signerKeyFingerprintBlacklist: List<SecureHash.SHA256> = emptyList()): JarScanningCordappLoader {
            logger.info("Looking for CorDapps in ${cordappDirs.distinct().joinToString(", ", "[", "]")}")
            val paths = cordappDirs.distinct().flatMap(this::jarUrlsInDirectory).map { it.restricted() }
            return JarScanningCordappLoader(paths, versionInfo, extraCordapps, signerKeyFingerprintBlacklist)
        }

        /**
         * Creates a CordappLoader loader out of a list of JAR URLs.
         *
         * @param scanJars Uses the JAR URLs provided for classpath scanning and Cordapp detection.
         */
        fun fromJarUrls(scanJars: List<URL>, versionInfo: VersionInfo = VersionInfo.UNKNOWN, extraCordapps: List<CordappImpl> = emptyList(),
                        cordappsSignerKeyFingerprintBlacklist: List<SecureHash.SHA256> = emptyList()): JarScanningCordappLoader {
            val paths = scanJars.map { it.restricted() }
            return JarScanningCordappLoader(paths, versionInfo, extraCordapps, cordappsSignerKeyFingerprintBlacklist)
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
                .map { url -> scanCordapp(url).use { it.toCordapp(url) } }
                .filter {
                    if (it.minimumPlatformVersion > versionInfo.platformVersion) {
                        logger.warn("Not loading CorDapp ${it.info.shortName} (${it.info.vendor}) as it requires minimum " +
                                "platform version ${it.minimumPlatformVersion} (This node is running version ${versionInfo.platformVersion}).")
                        false
                    } else {
                        true
                    }
                }
                .filter {
                    if (signerKeyFingerprintBlacklist.isEmpty()) {
                        true //Nothing blacklisted, no need to check
                    } else {
                        val certificates = it.jarPath.openStream().let(::JarInputStream).use(JarSignatureCollector::collectCertificates)
                        val blockedCertificates = certificates.filter { it.publicKey.hash.sha256() in signerKeyFingerprintBlacklist }
                        if (certificates.isEmpty() || (certificates - blockedCertificates).isNotEmpty())
                            true // Cordapp is not signed or it is signed by at least one non-blacklisted certificate
                        else {
                            logger.warn("Not loading CorDapp ${it.info.shortName} (${it.info.vendor}) as it is signed by development key(s) only: " +
                                    "${blockedCertificates.map { it.publicKey }}.")
                            false
                        }
                    }
                }
        cordapps.forEach(CordappResolver::register)
        return cordapps
    }

    private fun RestrictedScanResult.toCordapp(url: RestrictedURL): CordappImpl {
        val manifest: Manifest? = url.url.openStream().use { JarInputStream(it).manifest }
        val info = parseCordappInfo(manifest, CordappImpl.jarName(url.url))
        val minPlatformVersion = manifest?.get(CordappImpl.MIN_PLATFORM_VERSION)?.toIntOrNull() ?: 1
        val targetPlatformVersion = manifest?.get(CordappImpl.TARGET_PLATFORM_VERSION)?.toIntOrNull() ?: minPlatformVersion
        return CordappImpl(
                findContractClassNames(this),
                findInitiatedFlows(this),
                findRPCFlows(this),
                findServiceFlows(this),
                findSchedulableFlows(this),
                findServices(this),
                findWhitelists(url),
                findSerializers(this),
                findCustomSchemas(this),
                findAllFlows(this),
                url.url,
                info,
                getJarHash(url.url),
                minPlatformVersion,
                targetPlatformVersion,
                findNotaryService(this),
                explicitCordappClasses = findAllCordappClasses(this)
        )
    }

    private fun parseCordappInfo(manifest: Manifest?, defaultName: String): Cordapp.Info {
        if (manifest == null) return UNKNOWN_INFO

        /** new identifiers (Corda 4) */
        // is it a Contract Jar?
        val contractInfo = if (manifest[CordappImpl.CORDAPP_CONTRACT_NAME] != null) {
            Cordapp.Info.Contract(
                    shortName = manifest[CordappImpl.CORDAPP_CONTRACT_NAME] ?: defaultName,
                    vendor = manifest[CordappImpl.CORDAPP_CONTRACT_VENDOR] ?: CordappImpl.UNKNOWN_VALUE,
                    versionId = parseVersion(manifest[CordappImpl.CORDAPP_CONTRACT_VERSION], CordappImpl.CORDAPP_CONTRACT_VERSION),
                    licence = manifest[CordappImpl.CORDAPP_CONTRACT_LICENCE] ?: CordappImpl.UNKNOWN_VALUE
            )
        } else {
            null
        }

        // is it a Workflow (flows and services) Jar?
        val workflowInfo = if (manifest[CordappImpl.CORDAPP_WORKFLOW_NAME] != null) {
            Cordapp.Info.Workflow(
                    shortName = manifest[CordappImpl.CORDAPP_WORKFLOW_NAME] ?: defaultName,
                    vendor = manifest[CordappImpl.CORDAPP_WORKFLOW_VENDOR] ?: CordappImpl.UNKNOWN_VALUE,
                    versionId = parseVersion(manifest[CordappImpl.CORDAPP_WORKFLOW_VERSION], CordappImpl.CORDAPP_WORKFLOW_VERSION),
                    licence = manifest[CordappImpl.CORDAPP_WORKFLOW_LICENCE] ?: CordappImpl.UNKNOWN_VALUE
            )
        } else {
            null
        }

        when {
            // combined Contract and Workflow Jar?
            contractInfo != null && workflowInfo != null -> return Cordapp.Info.ContractAndWorkflow(contractInfo, workflowInfo)
            contractInfo != null -> return contractInfo
            workflowInfo != null -> return workflowInfo
        }

        return Cordapp.Info.Default(
                shortName = manifest["Name"] ?: defaultName,
                vendor = manifest["Implementation-Vendor"] ?: CordappImpl.UNKNOWN_VALUE,
                version = manifest["Implementation-Version"] ?: CordappImpl.UNKNOWN_VALUE,
                licence = CordappImpl.UNKNOWN_VALUE
        )
    }

    private fun parseVersion(versionStr: String?, attributeName: String): Int {
        if (versionStr == null) {
            throw CordappInvalidVersionException("Target versionId attribute $attributeName not specified. Please specify a whole number starting from 1.")
        }
        val version = versionStr.toIntOrNull()
                ?: throw CordappInvalidVersionException("Version identifier ($versionStr) for attribute $attributeName must be a whole number starting from 1.")
        if (version < 1) {
            throw CordappInvalidVersionException("Target versionId ($versionStr) for attribute $attributeName must not be smaller than 1.")
        }
        return version
    }

    private fun findNotaryService(scanResult: RestrictedScanResult): Class<out NotaryService>? {
        // Note: we search for implementations of both NotaryService and SinglePartyNotaryService as
        // the scanner won't find subclasses deeper down the hierarchy if any intermediate class is not
        // present in the CorDapp.
        val result = scanResult.getClassesWithSuperclass(NotaryService::class) +
                scanResult.getClassesWithSuperclass(SinglePartyNotaryService::class)
        if(!result.isEmpty()) {
            logger.info("Found notary service CorDapp implementations: " + result.joinToString(", "))
        }
        return result.firstOrNull()
    }

    private fun getJarHash(url: URL): SecureHash.SHA256 = url.openStream().readFully().sha256()

    private fun findServices(scanResult: RestrictedScanResult): List<Class<out SerializeAsToken>> {
        return scanResult.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
    }

    private fun findInitiatedFlows(scanResult: RestrictedScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, InitiatedBy::class)
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

    private fun findAllCordappClasses(scanResult: RestrictedScanResult): List<String> {
        return scanResult.getAllStandardClasses() + scanResult.getAllInterfaces()
    }

    private fun findContractClassNames(scanResult: RestrictedScanResult): List<String> {
        val contractClasses = coreContractClasses.flatMap { scanResult.getNamesOfClassesImplementing(it) }.distinct()
        for (contractClass in contractClasses) {
            contractClass.warnContractWithoutConstraintPropagation(appClassLoader)
        }
        return contractClasses
    }

    private fun findWhitelists(cordappJarPath: RestrictedURL): List<SerializationWhitelist> {
        val whitelists = URLClassLoader(arrayOf(cordappJarPath.url)).use {
            ServiceLoader.load(SerializationWhitelist::class.java, it).toList()
        }
        return whitelists.filter {
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
            val scanResult = ClassGraph().addClassLoader(appClassLoader).overrideClasspath(cordappJarPath.url).enableAllInfo().pooledScan()
            RestrictedScanResult(scanResult, cordappJarPath.qualifiedNamePrefix)
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

    // TODO Remove this class as rootPackageName is never non-null.
    /** @property rootPackageName only this package and subpackages may be extracted from [url], or null to allow all packages. */
    private data class RestrictedURL(val url: URL, val rootPackageName: String?) {
        val qualifiedNamePrefix: String get() = rootPackageName?.let { "$it." } ?: ""
    }

    private fun <T : Any> List<Class<out T>>.instances(): List<T> {
        return map { it.kotlin.objectOrNewInstance() }
    }

    private inner class RestrictedScanResult(private val scanResult: ScanResult, private val qualifiedNamePrefix: String) : AutoCloseable {
        fun getNamesOfClassesImplementing(type: KClass<*>): List<String> {
            return scanResult.getClassesImplementing(type.java.name).names.filter { it.startsWith(qualifiedNamePrefix) }
        }

        fun <T : Any> getClassesWithSuperclass(type: KClass<T>): List<Class<out T>> {
            return scanResult
                    .getSubclasses(type.java.name)
                    .names
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { it.isAbstractClass }
        }

        fun <T : Any> getClassesImplementing(type: KClass<T>): List<T> {
            return scanResult
                    .getClassesImplementing(type.java.name)
                    .names
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { it.isAbstractClass }
                    .map { it.kotlin.objectOrNewInstance() }
        }

        fun <T : Any> getClassesWithAnnotation(type: KClass<T>, annotation: KClass<out Annotation>): List<Class<out T>> {
            return scanResult
                    .getClassesWithAnnotation(annotation.java.name)
                    .names
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { Modifier.isAbstract(it.modifiers) }
        }

        fun <T : Any> getConcreteClassesOfType(type: KClass<T>): List<Class<out T>> {
            return scanResult
                    .getSubclasses(type.java.name)
                    .names
                    .filter { it.startsWith(qualifiedNamePrefix) }
                    .mapNotNull { loadClass(it, type) }
                    .filterNot { it.isAbstractClass }
        }

        fun getAllStandardClasses(): List<String> {
            return scanResult
                    .allStandardClasses
                    .names
                    .filter { it.startsWith(qualifiedNamePrefix) }
        }

        fun getAllInterfaces(): List<String> {
            return scanResult
                    .allInterfaces
                    .names
                    .filter { it.startsWith(qualifiedNamePrefix) }
        }

        override fun close() = scanResult.close()
    }
}

/**
 * Thrown when scanning CorDapps.
 */
class MultipleCordappsForFlowException(message: String) : Exception(message)

/**
 * Thrown if an exception occurs whilst parsing version identifiers within cordapp configuration
 */
class CordappInvalidVersionException(msg: String) : Exception(msg)

abstract class CordappLoaderTemplate : CordappLoader {

    companion object {

        private val logger = contextLogger()
    }

    override val flowCordappMap: Map<Class<out FlowLogic<*>>, Cordapp> by lazy {
        cordapps.flatMap { corDapp -> corDapp.allFlows.map { flow -> flow to corDapp } }
                .groupBy { it.first }
                .mapValues { entry ->
                    if (entry.value.size > 1) {
                        logger.error("There are multiple CorDapp JARs on the classpath for flow " +
                                "${entry.value.first().first.name}: [ ${entry.value.joinToString { it.second.jarPath.toString() }} ].")
                        entry.value.forEach { (_, cordapp) ->
                            ZipInputStream(cordapp.jarPath.openStream()).use { zip ->
                                val ident = BigInteger(64, Random()).toString(36)
                                logger.error("Contents of: ${cordapp.jarPath} will be prefaced with: $ident")
                                var e = zip.nextEntry
                                while (e != null) {
                                    logger.error("$ident\t ${e.name}")
                                    e = zip.nextEntry
                                }
                            }
                        }
                        throw MultipleCordappsForFlowException("There are multiple CorDapp JARs on the classpath for flow " +
                                "${entry.value.first().first.name}: [ ${entry.value.joinToString { it.second.jarPath.toString() }} ].")
                    }
                    entry.value.single().second
                }
    }

    override val cordappSchemas: Set<MappedSchema> by lazy {
        cordapps.flatMap { it.customSchemas }.toSet()
    }
}
