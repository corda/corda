package net.corda.bridge

import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.config.BridgeConfigHelper
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.contextLogger
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

class FirewallCmdLineOptions {

    companion object {
        val logger by lazy { contextLogger() }

        private fun Path.defaultConfigFile(): Path {
            val newStyleConfig = (this / "firewall.conf")
            return if (newStyleConfig.exists()) {
                newStyleConfig
            } else {
                val oldStyleConfig = (this / "bridge.conf")
                if (oldStyleConfig.exists()) {
                    logger.warn("Old style config 'bridge.conf' will be used. To prevent this warning in the future, please rename to 'firewall.conf'.")
                    oldStyleConfig
                } else {
                    throw IllegalArgumentException("Neither new style config 'firewall.conf', nor old style 'bridge.conf' can be found")
                }
            }
        }
    }

    @Option(
            names = ["-b", BASE_DIR],
            description = ["The firewall working directory where all the files are kept."]
    )
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()

    @Option(
            names = ["-f", "--config-file"],
            description = ["The path to the config file. By default this is firewall.conf in the base directory."]
    )
    private var _configFile: Path? = null

    fun loadConfig(): FirewallConfiguration {
        val configFile = _configFile ?: baseDirectory.defaultConfigFile()
        return BridgeConfigHelper.loadConfig(baseDirectory, configFile)
    }
}