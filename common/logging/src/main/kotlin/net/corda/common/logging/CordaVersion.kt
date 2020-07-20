package net.corda.common.logging

import java.util.jar.Manifest

class CordaVersion  {
    companion object {
        private const val UNKNOWN = "Unknown"
        const val platformEditionCode = "OS"

        private val manifests: List<Manifest> = Thread.currentThread()
                .contextClassLoader.getResources("META-INF/MANIFEST.MF")
                .toList()
                .map { Manifest(it.openStream()) }

        private fun manifestValue(name: String): String? =
                manifests.first { it.mainAttributes.getValue(name) != null }.mainAttributes.getValue(name)

        val releaseVersion: String by lazy { manifestValue("Corda-Release-Version") ?: UNKNOWN }
        val revision: String by lazy { manifestValue("Corda-Revision") ?: UNKNOWN }
        val vendor: String by lazy { manifestValue("Corda-Vendor") ?: UNKNOWN }
        val platformVersion: Int by lazy { manifestValue("Corda-Platform-Version")?.toInt() ?: 1 }
        val docsLink: String by lazy { manifestValue("Corda-Docs-Link") ?: UNKNOWN }

        internal val semanticVersion: String by lazy { if(releaseVersion == UNKNOWN) CURRENT_MAJOR_RELEASE else releaseVersion }
    }

    fun getVersion(): Array<String> {
        return if (releaseVersion != UNKNOWN && revision != UNKNOWN) {
            arrayOf("Version: $releaseVersion", "Revision: $revision", "Platform Version: $platformVersion", "Vendor: $vendor")
        } else {
            arrayOf("No version data is available in the MANIFEST file.")
        }
    }
}