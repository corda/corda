package net.corda.node.internal.cordapp

import net.corda.core.internal.cordapp.CordappImpl
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

operator fun Manifest.set(key: String, value: String) {
    mainAttributes.putValue(key, value)
}

fun Manifest?.toCordappInfo(defaultShortName: String): CordappImpl.Info {
    var info = CordappImpl.Info.UNKNOWN
    (this?.mainAttributes?.getValue("Name") ?: defaultShortName).let { shortName ->
        info = info.copy(shortName = shortName)
    }
    this?.mainAttributes?.getValue("Implementation-Vendor")?.let { vendor ->
        info = info.copy(vendor = vendor)
    }
    this?.mainAttributes?.getValue("Implementation-Version")?.let { version ->
        info = info.copy(version = version)
    }
    val minPlatformVersion = this?.mainAttributes?.getValue("Min-Platform-Version")?.toInt() ?: 1
    val targetPlatformVersion = this?.mainAttributes?.getValue("Target-Platform-Version")?.toInt() ?: minPlatformVersion
    info = info.copy(minPlatformVersion = minPlatformVersion, targetPlatformVersion = targetPlatformVersion)
    return info
}