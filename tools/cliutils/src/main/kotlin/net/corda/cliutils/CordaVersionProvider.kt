package net.corda.cliutils

import com.jcabi.manifests.Manifests
import picocli.CommandLine

/**
 * Simple version printing when command is called with --version or -V flag. Assuming that we reuse Corda-Release-Version and Corda-Revision
 * in the manifest file.
 */
class CordaVersionProvider : CommandLine.IVersionProvider {
    companion object {
        val releaseVersion: String by lazy { Manifests.read("Corda-Release-Version") }
        val revision: String by lazy { Manifests.read("Corda-Revision") }
    }

    override fun getVersion(): Array<String> {
        return if (Manifests.exists("Corda-Release-Version") && Manifests.exists("Corda-Revision")) {
            arrayOf("Version: $releaseVersion", "Revision: $revision")
        } else {
            arrayOf("No version data is available in the MANIFEST file.")
        }
    }
}