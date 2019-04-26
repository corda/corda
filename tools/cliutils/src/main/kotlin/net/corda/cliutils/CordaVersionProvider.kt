package net.corda.cliutils

import picocli.CommandLine
import net.corda.common.logging.CordaVersion

/**
 * Simple version printing when command is called with --version or -V flag. Assuming that we reuse Corda-Release-Version and Corda-Revision
 * in the manifest file.
 */
class CordaVersionProvider : CommandLine.IVersionProvider {
    val version = CordaVersion()

    override fun getVersion(): Array<String> {
        return version.getVersion()
    }
}