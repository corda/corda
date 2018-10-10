package net.corda.node.internal.cordapp

import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.CordappImpl.Info.Companion.UNKNOWN_VALUE
import java.util.jar.Attributes
import java.util.jar.Manifest

fun createTestManifest(name: String, title: String, version: String, vendor: String): Manifest {
    val manifest = Manifest()

    // Mandatory manifest attribute. If not present, all other entries are silently skipped.
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"

    manifest["Name"] = name

    manifest["Specification-Title"] = title
    manifest["Specification-Version"] = version
    manifest["Specification-Vendor"] = vendor

    manifest["Implementation-Title"] = title
    manifest["Implementation-Version"] = version
    manifest["Implementation-Vendor"] = vendor

    return manifest
}

operator fun Manifest.set(key: String, value: String): String? {
    return mainAttributes.putValue(key, value)
}

operator fun Manifest.get(key: String): String? = mainAttributes.getValue(key)

fun Manifest.toCordappInfo(defaultShortName: String): CordappImpl.Info {
    val shortName = this["Name"] ?: defaultShortName
    val vendor = this["Implementation-Vendor"] ?: UNKNOWN_VALUE
    val version = this["Implementation-Version"] ?: UNKNOWN_VALUE
    val minPlatformVersion = this["Min-Platform-Version"]?.toIntOrNull() ?: 1
    val targetPlatformVersion = this["Target-Platform-Version"]?.toIntOrNull() ?: minPlatformVersion
    return CordappImpl.Info(
            shortName = shortName,
            vendor = vendor,
            version = version,
            minimumPlatformVersion = minPlatformVersion,
            targetPlatformVersion = targetPlatformVersion
    )
}
