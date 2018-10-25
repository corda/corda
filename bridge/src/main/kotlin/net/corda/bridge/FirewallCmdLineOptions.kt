package net.corda.bridge

import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.config.BridgeConfigHelper
import net.corda.bridge.services.config.parseAsFirewallConfiguration
import net.corda.core.internal.div
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

class FirewallCmdLineOptions {
    @Option(
            names = ["-b", "--base-directory"],
            description = ["The firewall working directory where all the files are kept."]
    )
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()

    @Option(
            names = ["-f", "--config-file"],
            description = ["The path to the config file. By default this is firewall.conf in the base directory."]
    )
    private var _configFile: Path? = null
    val configFile: Path get() = _configFile ?: (baseDirectory / "firewall.conf")

    fun loadConfig(): FirewallConfiguration {
        return BridgeConfigHelper.loadConfig(baseDirectory, configFile).parseAsFirewallConfiguration()
    }
}