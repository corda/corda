package net.corda.cliutils

import com.jcabi.manifests.Manifests
import picocli.CommandLine

/**
 * Simple version printing when command is called with --version or -V flag. Assuming that we reuse Corda-Release-Version and Corda-Revision
 * in the manifest file.
 */
class CordaVersionProvider : CommandLine.IVersionProvider {
    companion object {
        private const val UNKNOWN = "Unknown"
        const val current_major_release = "4.0-SNAPSHOT"
        const val platformEditionCode = "OS"

        private fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null

        val releaseVersion: String by lazy { manifestValue("Corda-Release-Version") ?: UNKNOWN }
        val revision: String by lazy { manifestValue("Corda-Revision") ?: UNKNOWN }
        val vendor: String by lazy { manifestValue("Corda-Vendor") ?: UNKNOWN }
        val platformVersion: Int by lazy { manifestValue("Corda-Platform-Version")?.toInt() ?: 1 }

        internal val semanticVersion: String by lazy { if(releaseVersion == UNKNOWN) current_major_release else releaseVersion }
    }

    override fun getVersion(): Array<String> {
        return if (Manifests.exists("Corda-Release-Version") && Manifests.exists("Corda-Revision")) {
            arrayOf("Version: $releaseVersion", "Revision: $revision", "Platform Version: $platformVersion", "Vendor: $vendor")
        } else {
            arrayOf("No version data is available in the MANIFEST file.")
        }
    }
}