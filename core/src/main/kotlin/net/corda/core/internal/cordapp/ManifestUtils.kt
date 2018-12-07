package net.corda.core.internal.cordapp

import net.corda.core.utilities.loggerFor
import java.util.jar.Attributes
import java.util.jar.Manifest

fun createTestManifest(name: String, version: String, vendor: String, licence: String, targetVersion: Int): Manifest {
    val manifest = Manifest()

    // Mandatory manifest attribute. If not present, all other entries are silently skipped.
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"

    manifest[CORDAPP_CONTRACT_NAME] = name
    manifest[CORDAPP_CONTRACT_VERSION] = version
    manifest[CORDAPP_CONTRACT_VENDOR] = vendor
    manifest[CORDAPP_CONTRACT_LICENCE] = licence

    manifest[CORDAPP_WORKFLOW_NAME] = name
    manifest[CORDAPP_WORKFLOW_VERSION] = version
    manifest[CORDAPP_WORKFLOW_VENDOR] = vendor
    manifest[CORDAPP_WORKFLOW_LICENCE] = licence

    manifest[TARGET_PLATFORM_VERSION] = targetVersion.toString()
    manifest[MIN_PLATFORM_VERSION] = "1"

    return manifest
}

operator fun Manifest.set(key: String, value: String): String? {
    return mainAttributes.putValue(key, value)
}

operator fun Manifest.set(key: Attributes.Name, value: String): Any? {
    return mainAttributes.put(key, value)
}

operator fun Manifest.get(key: String): String? = mainAttributes.getValue(key)

val Manifest.targetPlatformVersion: Int
    get() {
        val minPlatformVersion = mainAttributes.getValue(MIN_PLATFORM_VERSION)?.toInt() ?: 1
        return mainAttributes.getValue(TARGET_PLATFORM_VERSION)?.toInt() ?: minPlatformVersion
    }

fun Manifest.toCordappInfo(defaultName: String): CordappInfo {

    val log = loggerFor<Manifest>()

    val minPlatformVersion = this[MIN_PLATFORM_VERSION]?.toIntOrNull() ?: 1
    val targetPlatformVersion = this[TARGET_PLATFORM_VERSION]?.toIntOrNull() ?: minPlatformVersion

    /** new identifiers (Corda 4) */
    // is it a Contract Jar?
    if (this[CORDAPP_CONTRACT_NAME] != null) {
        val name = this[CORDAPP_CONTRACT_NAME] ?: defaultName
        val version = try {
            Integer.valueOf(this[CORDAPP_CONTRACT_VERSION])
        } catch (nfe: NumberFormatException) {
            log.warn("Invalid version identifier ${this[CORDAPP_CONTRACT_VERSION]}. Defaulting to $DEFAULT_CORDAPP_VERSION")
            DEFAULT_CORDAPP_VERSION
        } ?: DEFAULT_CORDAPP_VERSION
        val vendor = this[CORDAPP_CONTRACT_VENDOR] ?: UNKNOWN_VALUE
        val licence = this[CORDAPP_CONTRACT_LICENCE] ?: UNKNOWN_VALUE
        return Contract(
                name = name,
                vendor = vendor,
                versionId = version,
                licence = licence,
                minimumPlatformVersion = minPlatformVersion,
                targetPlatformVersion = targetPlatformVersion
        )
    }
    // is it a Contract Jar?
    if (this[CORDAPP_WORKFLOW_NAME] != null) {
        val name = this[CORDAPP_WORKFLOW_NAME] ?: defaultName
        val version = try {
            Integer.valueOf(this[CORDAPP_WORKFLOW_VERSION])
        } catch (nfe: NumberFormatException) {
            log.warn("Invalid version identifier ${this[CORDAPP_CONTRACT_VERSION]}. Defaulting to $DEFAULT_CORDAPP_VERSION")
            DEFAULT_CORDAPP_VERSION
        } ?: DEFAULT_CORDAPP_VERSION
        val vendor = this[CORDAPP_WORKFLOW_VENDOR] ?: UNKNOWN_VALUE
        val licence = this[CORDAPP_WORKFLOW_LICENCE] ?: UNKNOWN_VALUE
        return Workflow(
                name = name,
                vendor = vendor,
                versionId = version,
                licence = licence,
                minimumPlatformVersion = minPlatformVersion,
                targetPlatformVersion = targetPlatformVersion
        )
    }

    /** need to maintain backwards compatibility so use old identifiers if existent */
    val shortName = this["Name"] ?: defaultName
    val vendor = this["Implementation-Vendor"] ?: UNKNOWN_VALUE
    val version = this["Implementation-Version"] ?: UNKNOWN_VALUE
    return CordappImpl.Info(
            shortName = shortName,
            vendor = vendor,
            version = version,
            minimumPlatformVersion = minPlatformVersion,
            targetPlatformVersion = targetPlatformVersion
    )
}
