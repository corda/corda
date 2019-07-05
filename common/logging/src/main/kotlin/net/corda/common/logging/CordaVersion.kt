package net.corda.common.logging

import com.jcabi.manifests.Manifests

class CordaVersion  {
    companion object {
        private const val UNKNOWN = "Unknown"
        const val platformEditionCode = "OS"

        private fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null

        val releaseVersion: String by lazy { manifestValue("Corda-Release-Version") ?: UNKNOWN }
        val revision: String by lazy { manifestValue("Corda-Revision") ?: UNKNOWN }
        val vendor: String by lazy { manifestValue("Corda-Vendor") ?: UNKNOWN }
        val platformVersion: Int by lazy { manifestValue("Corda-Platform-Version")?.toInt() ?: 1 }

        internal val semanticVersion: String by lazy { if(releaseVersion == UNKNOWN) CURRENT_MAJOR_RELEASE else releaseVersion }
    }

    fun getVersion(): Array<String> {
        return if (Manifests.exists("Corda-Release-Version") && Manifests.exists("Corda-Revision")) {
            arrayOf("Version: $releaseVersion", "Revision: $revision", "Platform Version: $platformVersion", "Vendor: $vendor")
        } else {
            arrayOf("No version data is available in the MANIFEST file.")
        }
    }
}