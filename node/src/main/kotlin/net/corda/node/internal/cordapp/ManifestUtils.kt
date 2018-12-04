package net.corda.node.internal.cordapp

import net.corda.core.internal.cordapp.*
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

    manifest["Target-Platform-Version"] = targetVersion.toString()

    return manifest
}

operator fun Manifest.set(key: String, value: String): String? {
    return mainAttributes.putValue(key, value)
}

operator fun Manifest.set(key: Attributes.Name, value: String): Any? {
    return mainAttributes.put(key, value)
}

operator fun Manifest.get(key: String): String? = mainAttributes.getValue(key)

fun Manifest.toCordappInfo(defaultName: String): CordappInfo {

    val log = loggerFor<Manifest>()

    val minPlatformVersion = this["Min-Platform-Version"]?.toIntOrNull() ?: 1
    val targetPlatformVersion = this["Target-Platform-Version"]?.toIntOrNull() ?: minPlatformVersion

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
    if (this["Cordapp-Worflow-Name"] != null) {
        val name = this["Cordapp-Worflow-Name"] ?: defaultName
        val version = try {
            Integer.valueOf(this["Cordapp-Worflow-Version"])
        } catch (nfe: NumberFormatException) {
            log.warn("Invalid version identifier ${this[CORDAPP_CONTRACT_VERSION]}. Defaulting to $DEFAULT_CORDAPP_VERSION")
            DEFAULT_CORDAPP_VERSION
        } ?: DEFAULT_CORDAPP_VERSION
        val vendor = this["Cordapp-Worflow-Vendor"] ?: UNKNOWN_VALUE
        val licence = this["Cordapp-Worflow-Licence"] ?: UNKNOWN_VALUE
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
