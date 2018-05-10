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

import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.cordapp.CordappImpl
import java.util.*
import java.util.jar.Attributes
import java.util.jar.Manifest

internal fun createTestManifest(name: String, title: String, jarUUID: UUID): Manifest {
    val manifest = Manifest()
    val version = "test-$jarUUID"
    val vendor = "R3"

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

internal operator fun Manifest.set(key: String, value: String) {
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
    return unknown
}