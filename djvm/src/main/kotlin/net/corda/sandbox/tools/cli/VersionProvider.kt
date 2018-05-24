package net.corda.sandbox.tools.cli

import com.jcabi.manifests.Manifests
import picocli.CommandLine.IVersionProvider

/**
 * Get the version number to use for the tool.
 */
@Suppress("KDocMissingDocumentation")
class VersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> = arrayOf(
            Manifests.read("Corda-Release-Version"),
            Manifests.read("Build-Date")
    )
}
