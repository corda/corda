package net.corda.node.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.cordapp.CordappImpl
import java.util.*
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

internal fun Manifest?.toCordappInfo(defaultShortName: String): Cordapp.Info {

    var unknown = CordappImpl.Info.UNKNOWN
    (this?.mainAttributes?.getValue("Name") ?: defaultShortName).let { shortName ->
        unknown = unknown.copy(shortName = shortName)
    }
    this?.mainAttributes?.getValue("Implementation-Vendor")?.let { vendor ->
        unknown = unknown.copy(vendor = vendor)
    }
    this?.mainAttributes?.getValue("Implementation-Version")?.let { version ->
        unknown = unknown.copy(version = version)
    }
    this?.mainAttributes?.getValue("Min-Platform-Version")?.let { minPlatformVersion ->
        unknown.copy(minPlatformVersion = minPlatformVersion.toInt())
    }
    this?.mainAttributes?.getValue("Target-Platform-Version")?.let { targetPlatformVersion ->
        unknown.copy(targetPlatformVersion = targetPlatformVersion.toInt())
    }
    return unknown
}