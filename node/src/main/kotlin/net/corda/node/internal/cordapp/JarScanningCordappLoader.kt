package net.corda.node.internal.cordapp

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.ClassInfoList
import io.github.classgraph.ScanResult
import net.corda.common.logging.errorReporting.CordappErrors
import net.corda.common.logging.errorReporting.ErrorCode
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.RotatedKeys
import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.internal.JarSignatureCollector
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.CordappImpl.Companion.UNKNOWN_INFO
import net.corda.core.internal.cordapp.KotlinMetadataVersion
import net.corda.core.internal.cordapp.LanguageVersion
import net.corda.core.internal.cordapp.get
import net.corda.core.internal.flatMapToSet
import net.corda.core.internal.groupByMultipleKeys
import net.corda.core.internal.hash
import net.corda.core.internal.isAbstractClass
import net.corda.core.internal.loadClassOfType
import net.corda.core.internal.location
import net.corda.core.internal.mapToSet
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.internal.objectOrNewInstance
import net.corda.core.internal.pooledScan
import net.corda.core.internal.telemetry.TelemetryComponent
import net.corda.core.internal.toPath
import net.corda.core.internal.toTypedArray
import net.corda.core.internal.warnContractWithoutConstraintPropagation
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.node.VersionInfo
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.coreContractClasses
import net.corda.serialization.internal.DefaultWhitelist
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.TreeSet
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isSameFileAs
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.useDirectoryEntries
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Handles CorDapp loading and classpath scanning of CorDapp JARs
 *
 * @property cordappJars The classpath of cordapp JARs
 * @property legacyContractJars Legacy contract CorDapps (4.11 or earlier) needed for backwards compatibility with 4.11 nodes.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class JarScanningCordappLoader(private val cordappJars: Set<Path>,
                               private val legacyContractJars: Set<Path> = emptySet(),
                               private val versionInfo: VersionInfo = VersionInfo.UNKNOWN,
                               private val extraCordapps: List<CordappImpl> = emptyList(),
                               private val signerKeyFingerprintBlacklist: List<SecureHash> = emptyList(),
                               private val rotatedKeys: RotatedKeys = RotatedKeys()) : CordappLoader {
    companion object {
        private val logger = contextLogger()

        const val LEGACY_CONTRACTS_DIR_NAME = "legacy-contracts"

        /**
         * Creates a CordappLoader from multiple directories.
         *
         * @param cordappDirs Directories used to scan for CorDapp JARs.
         * @param legacyContractsDir Directory containing legacy contract CorDapps (4.11 or earlier).
         */
        fun fromDirectories(cordappDirs: Collection<Path>,
                            legacyContractsDir: Path? = null,
                            versionInfo: VersionInfo = VersionInfo.UNKNOWN,
                            extraCordapps: List<CordappImpl> = emptyList(),
                            signerKeyFingerprintBlacklist: List<SecureHash> = emptyList(),
                            rotatedKeys: RotatedKeys = RotatedKeys()): JarScanningCordappLoader {
            logger.info("Looking for CorDapps in ${cordappDirs.toSet().joinToString(", ", "[", "]")}")
            val cordappJars = cordappDirs
                    .asSequence()
                    .flatMap { if (it.exists()) it.listDirectoryEntries("*.jar") else emptyList() }
                    .toSet()
            val legacyContractJars = legacyContractsDir?.useDirectoryEntries("*.jar") { it.toSet() } ?: emptySet()
            return JarScanningCordappLoader(cordappJars, legacyContractJars, versionInfo, extraCordapps, signerKeyFingerprintBlacklist, rotatedKeys)
        }
    }

    init {
        logger.debug { "cordappJars: $cordappJars" }
        logger.debug { "legacyContractJars: $legacyContractJars" }
    }

    override val appClassLoader = URLClassLoader(cordappJars.stream().map { it.toUri().toURL() }.toTypedArray(), javaClass.classLoader)

    private val internal by lazy(::InternalHolder)

    override val cordapps: List<CordappImpl>
        get() = internal.nonLegacyCordapps

    override val legacyContractCordapps: List<CordappImpl>
        get() = internal.legacyContractCordapps

    override fun close() = appClassLoader.close()

    private inner class InternalHolder {
        val nonLegacyCordapps = cordappJars.mapTo(ArrayList(), ::scanCordapp)
        val legacyContractCordapps = legacyContractJars.map(::scanCordapp)

        init {
            commonChecks(nonLegacyCordapps, LanguageVersion::isNonLegacyCompatible)
            nonLegacyCordapps += extraCordapps
            if (legacyContractCordapps.isNotEmpty()) {
                commonChecks(legacyContractCordapps, LanguageVersion::isLegacyCompatible)
                checkLegacyContracts()
            }
        }

        private fun commonChecks(cordapps: List<CordappImpl>, compatibilityProperty: KProperty1<LanguageVersion, Boolean>) {
            for (cordapp in cordapps) {
                check(compatibilityProperty(cordapp.languageVersion)) {
                    val isLegacyCompatibleCheck = compatibilityProperty == LanguageVersion::isLegacyCompatible
                    val msg = when {
                        isLegacyCompatibleCheck -> "not legacy; please remove or place it in the node's CorDapps directory."
                        cordapp.contractClassNames.isEmpty() -> "legacy (should be 4.12 or later)"
                        else -> "legacy contracts; please place it in the node's '$LEGACY_CONTRACTS_DIR_NAME' directory."
                    }
                    "CorDapp ${cordapp.jarFile} is $msg"
                }
            }
            checkInvalidCordapps(cordapps)
            checkDuplicateCordapps(cordapps)
            // The same contract may occur in both 4.11 and 4.12 CorDapps for ledger compatibility, so we only check for overlap within each
            // compatibility group
            checkContractOverlap(cordapps)
        }

        private fun checkInvalidCordapps(cordapps: List<CordappImpl>) {
            val invalidCordapps = LinkedHashMap<String, CordappImpl>()

            for (cordapp in cordapps) {
                if (cordapp.minimumPlatformVersion > versionInfo.platformVersion) {
                    logger.error("Not loading CorDapp ${cordapp.info.shortName} (${cordapp.info.vendor}) as it requires minimum " +
                            "platform version ${cordapp.minimumPlatformVersion} (This node is running version ${versionInfo.platformVersion}).")
                    invalidCordapps["CorDapp requires minimumPlatformVersion ${cordapp.minimumPlatformVersion}, but this node is running version ${versionInfo.platformVersion}"] = cordapp
                }
                if (signerKeyFingerprintBlacklist.isNotEmpty()) {
                    val certificates = cordapp.jarPath.openStream().let(::JarInputStream).use(JarSignatureCollector::collectCertificates)
                    val blockedCertificates = certificates.filterTo(HashSet()) { it.publicKey.hash.sha256() in signerKeyFingerprintBlacklist }
                    if (certificates.isNotEmpty() && (certificates - blockedCertificates).isEmpty()) {
                        logger.error("Not loading CorDapp ${cordapp.info.shortName} (${cordapp.info.vendor}) as it is signed by blacklisted " +
                                "key(s) only (probably development key): ${blockedCertificates.map { it.publicKey }}.")
                        invalidCordapps["Corresponding contracts are signed by blacklisted key(s) only (probably development key),"] = cordapp
                    }
                }
            }

            if (invalidCordapps.isNotEmpty()) {
                throw InvalidCordappException("Invalid Cordapps found, that couldn't be loaded: " +
                        "${invalidCordapps.map { "Problem: ${it.key} in Cordapp ${it.value.jarFile}" }}, ")
            }
        }

        private fun checkDuplicateCordapps(cordapps: List<CordappImpl>) {
            for (group in cordapps.groupBy { it.jarHash }.values) {
                if (group.size > 1) {
                    throw DuplicateCordappsInstalledException(group[0], group.drop(1))
                }
            }
        }

        private fun checkContractOverlap(cordapps: List<CordappImpl>) {
            cordapps.groupByMultipleKeys(CordappImpl::contractClassNames) { contract, cordapp1, cordapp2 ->
                throw IllegalStateException("Contract $contract occuring in multiple CorDapps (${cordapp1.name}, ${cordapp2.name}). " +
                        "Please remove the previous version when upgrading to a new version.")
            }
        }

        private fun checkLegacyContracts() {
            for (legacyCordapp in legacyContractCordapps) {
                if (legacyCordapp.contractClassNames.isEmpty()) continue
                logger.debug { "Contracts CorDapp ${legacyCordapp.name} is legacy (4.11 or older), searching for corresponding 4.12+ contracts" }
                for (legacyContract in legacyCordapp.contractClassNames) {
                    val newerCordapp = nonLegacyCordapps.find { legacyContract in it.contractClassNames }
                    checkNotNull(newerCordapp) {
                        "Contract $legacyContract in legacy CorDapp (4.11 or older) '${legacyCordapp.jarFile}' does not have a " +
                                "corresponding newer version (4.12 or later). Please add this corresponding CorDapp or remove the legacy one."
                    }
                    check(newerCordapp.contractVersionId > legacyCordapp.contractVersionId) {
                        "Newer contract CorDapp '${newerCordapp.jarFile}' does not have a higher versionId " +
                                "(${newerCordapp.contractVersionId}) than corresponding legacy contract CorDapp " +
                                "'${legacyCordapp.jarFile}' (${legacyCordapp.contractVersionId})"
                    }
                    checkSignersMatch(legacyCordapp, newerCordapp)
                }
            }
        }

        private fun checkSignersMatch(legacyCordapp: CordappImpl, nonLegacyCordapp: CordappImpl) {
            val legacySigners = legacyCordapp.jarPath.openStream().let(::JarInputStream).use(JarSignatureCollector::collectSigners)
            val nonLegacySigners = nonLegacyCordapp.jarPath.openStream().let(::JarInputStream).use(JarSignatureCollector::collectSigners)
            check(rotatedKeys.canBeTransitioned(legacySigners, nonLegacySigners)) {
                "Newer contract CorDapp '${nonLegacyCordapp.jarFile}' signers do not match legacy contract CorDapp " +
                        "'${legacyCordapp.jarFile}' signers."
            }
        }

        private val CordappImpl.contractVersionId: Int
            get() = when (val info = info) {
                is Cordapp.Info.Contract -> info.versionId
                is Cordapp.Info.ContractAndWorkflow -> info.contract.versionId
                else -> 1
            }
    }

    private fun ScanResult.toCordapp(path: Path): CordappImpl {
        val manifest: Manifest? = JarInputStream(path.inputStream()).use { it.manifest }
        val info = parseCordappInfo(manifest, CordappImpl.jarName(path))
        val minPlatformVersion = manifest?.get(CordappImpl.MIN_PLATFORM_VERSION)?.toIntOrNull() ?: 1
        val targetPlatformVersion = manifest?.get(CordappImpl.TARGET_PLATFORM_VERSION)?.toIntOrNull() ?: minPlatformVersion
        val languageVersion = determineLanguageVersion(path)
        logger.debug { "$path: $languageVersion" }
        return CordappImpl(
                path,
                findContractClassNames(this),
                findInitiatedFlows(this),
                findRPCFlows(this),
                findServiceFlows(this),
                findSchedulableFlows(this),
                findServices(this),
                findTelemetryComponents(this),
                findWhitelists(path),
                findSerializers(this),
                findCheckpointSerializers(this),
                findCustomSchemas(this),
                findAllFlows(this),
                info,
                minPlatformVersion,
                targetPlatformVersion,
                languageVersion = languageVersion,
                notaryService = findNotaryService(this),
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
            throw CordappInvalidVersionException(
                    "Target versionId attribute $attributeName not specified. Please specify a whole number starting from 1.",
                    CordappErrors.MISSING_VERSION_ATTRIBUTE,
                    listOf(attributeName))
        }
        val version = versionStr.toIntOrNull()
                ?: throw CordappInvalidVersionException(
                        "Version identifier ($versionStr) for attribute $attributeName must be a whole number starting from 1.",
                        CordappErrors.INVALID_VERSION_IDENTIFIER,
                        listOf(versionStr, attributeName))
        if (version < PlatformVersionSwitches.FIRST_VERSION) {
            throw CordappInvalidVersionException(
                    "Target versionId ($versionStr) for attribute $attributeName must not be smaller than 1.",
                    CordappErrors.INVALID_VERSION_IDENTIFIER,
                    listOf(versionStr, attributeName))
        }
        return version
    }

    private fun findNotaryService(scanResult: ScanResult): Class<out NotaryService>? {
        // Note: we search for implementations of both NotaryService and SinglePartyNotaryService as
        // the scanner won't find subclasses deeper down the hierarchy if any intermediate class is not
        // present in the CorDapp.
        val result = scanResult.getClassesExtending(NotaryService::class) +
                scanResult.getClassesExtending(SinglePartyNotaryService::class)
        if (result.isNotEmpty()) {
            logger.info("Found notary service CorDapp implementations: " + result.joinToString(", "))
        }
        return result.firstOrNull()
    }

    private fun findServices(scanResult: ScanResult): List<Class<out SerializeAsToken>> {
        return scanResult.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
    }

    private fun findTelemetryComponents(scanResult: ScanResult): List<Class<out TelemetryComponent>> {
        return scanResult.getClassesImplementing(TelemetryComponent::class)
    }

    private fun findInitiatedFlows(scanResult: ScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesWithAnnotation(FlowLogic::class, InitiatedBy::class)
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

    private fun findAllFlows(scanResult: ScanResult): List<Class<out FlowLogic<*>>> {
        return scanResult.getClassesExtending(FlowLogic::class)
    }

    private fun findAllCordappClasses(scanResult: ScanResult): List<String> {
        val cordappClasses = ArrayList<String>()
        scanResult.allStandardClasses.mapTo(cordappClasses) { it.name }
        scanResult.allInterfaces.mapTo(cordappClasses) { it.name }
        return cordappClasses
    }

    private fun findContractClassNames(scanResult: ScanResult): List<String> {
        val contractClasses = coreContractClasses.flatMapToSet(scanResult::getClassesImplementing)
        for (contractClass in contractClasses) {
            contractClass.name.warnContractWithoutConstraintPropagation(appClassLoader)
        }
        return contractClasses.map { it.name }
    }

    private fun findWhitelists(cordappJar: Path): List<SerializationWhitelist> {
        val whitelists = ServiceLoader.load(SerializationWhitelist::class.java, appClassLoader).toList()
        return whitelists.filter {
            it.javaClass.location.toPath().isSameFileAs(cordappJar)
        } + DefaultWhitelist // Always add the DefaultWhitelist to the whitelist for an app.
    }

    private fun findSerializers(scanResult: ScanResult): List<SerializationCustomSerializer<*, *>> {
        return scanResult.getClassesImplementing(SerializationCustomSerializer::class).map { it.kotlin.objectOrNewInstance() }
    }

    private fun findCheckpointSerializers(scanResult: ScanResult): List<CheckpointCustomSerializer<*, *>> {
        return scanResult.getClassesImplementing(CheckpointCustomSerializer::class).map { it.kotlin.objectOrNewInstance() }
    }

    private fun findCustomSchemas(scanResult: ScanResult): Set<MappedSchema> {
        return scanResult.getClassesExtending(MappedSchema::class).mapToSet { it.kotlin.objectOrNewInstance() }
    }

    private fun scanCordapp(cordappJar: Path): CordappImpl {
        logger.info("Scanning CorDapp ${cordappJar.absolutePathString()}")
        return ClassGraph()
                .overrideClasspath(cordappJar.absolutePathString())
                .enableAllInfo()
                .pooledScan()
                .use { it.toCordapp(cordappJar) }
    }

    private fun <T : Any> loadClass(className: String, type: KClass<T>): Class<out T>? {
        return try {
            loadClassOfType(type.java, className, false, appClassLoader)
        } catch (e: ClassCastException) {
            logger.warn("As $className must be a sub-type of ${type.java.name}")
            null
        } catch (e: Exception) {
            logger.warn("Unable to load class $className", e)
            null
        }
    }

    private fun <T : Any> ScanResult.getClassesExtending(type: KClass<T>): List<Class<out T>> {
        return getSubclasses(type.java).getAllConcreteClasses(type)
    }

    private fun <T : Any> ScanResult.getClassesImplementing(type: KClass<T>): List<Class<out T>> {
        return getClassesImplementing(type.java).getAllConcreteClasses(type)
    }

    private fun <T : Any> ScanResult.getClassesWithAnnotation(type: KClass<T>, annotation: KClass<out Annotation>): List<Class<out T>> {
        return getClassesWithAnnotation(annotation.java).getAllConcreteClasses(type)
    }

    private fun <T : Any> ClassInfoList.getAllConcreteClasses(type: KClass<T>): List<Class<out T>> {
        return mapNotNull { loadClass(it.name, type)?.takeUnless(Class<*>::isAbstractClass) }
    }

    private fun ScanResult.determineLanguageVersion(cordappJar: Path): LanguageVersion {
        val allClasses = allClassesAsMap.values
        if (allClasses.isEmpty()) {
            return LanguageVersion.Data
        }
        val classFileMajorVersion = allClasses.maxOf { it.classfileMajorVersion }
        val kotlinMetadataVersion = allClasses
                .mapNotNullTo(TreeSet()) { it.kotlinMetadataVersion() }
                .let { kotlinMetadataVersions ->
                    // If there's more than one minor version of Kotlin
                    if (kotlinMetadataVersions.size > 1 && kotlinMetadataVersions.mapToSet { it.copy(patch = 0) }.size > 1) {
                        logger.warn("CorDapp $cordappJar comprised of multiple Kotlin versions (kotlinMetadataVersions=$kotlinMetadataVersions). " +
                                "This may cause compatibility issues.")
                    }
                    kotlinMetadataVersions.takeIf { it.isNotEmpty() }?.last()
                }
        try {
            return LanguageVersion.Bytecode(classFileMajorVersion, kotlinMetadataVersion)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Unable to load CorDapp $cordappJar: ${e.message}")
        }
    }

    private fun ClassInfo.kotlinMetadataVersion(): KotlinMetadataVersion? {
        val kotlinMetadata = getAnnotationInfo(Metadata::class.java) ?: return null
        val kotlinMetadataVersion = KotlinMetadataVersion.from(kotlinMetadata.parameterValues.get("mv").value as IntArray)
        logger.trace { "$name: $kotlinMetadataVersion" }
        return kotlinMetadataVersion
    }
}

/**
 * Thrown when scanning CorDapps.
 */
class MultipleCordappsForFlowException(
        message: String,
        flowName: String,
        jars: String
) : CordaRuntimeException(message), ErrorCode<CordappErrors> {
    override val code = CordappErrors.MULTIPLE_CORDAPPS_FOR_FLOW
    override val parameters = listOf(flowName, jars)
}

/**
 * Thrown if an exception occurs whilst parsing version identifiers within cordapp configuration
 */
class CordappInvalidVersionException(
        msg: String,
        override val code: CordappErrors,
        override val parameters: List<Any> = listOf()
) : CordaRuntimeException(msg), ErrorCode<CordappErrors>

/**
 * Thrown if duplicate CorDapps are installed on the node
 */
class DuplicateCordappsInstalledException(app: Cordapp, duplicates: Collection<Cordapp>)
    : CordaRuntimeException("IllegalStateExcepion", "The CorDapp (name: ${app.info.shortName}, file: ${app.name}) " +
        "is installed multiple times on the node. The following files correspond to the exact same content: " +
        "${duplicates.map { it.name }}", null), ErrorCode<CordappErrors> {
    override val code = CordappErrors.DUPLICATE_CORDAPPS_INSTALLED
    override val parameters = listOf(app.info.shortName, app.name, duplicates.map { it.name })
}

/**
 * Thrown if an exception occurs during loading cordapps.
 */
class InvalidCordappException(message: String) : CordaRuntimeException(message)
