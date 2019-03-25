package net.corda.node

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.internal.div
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.Valid
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

open class SharedNodeCmdLineOptions {
    private companion object {
        private val logger by lazy { loggerFor<SharedNodeCmdLineOptions>() }
    }
    @Option(
            names = ["-b", BASE_DIR],
            description = ["The node working directory where all the files are kept."]
    )
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()

    @Option(
            names = ["-f", "--config-file"],
            description = ["The path to the config file. By default this is node.conf in the base directory."]
    )
    private var _configFile: Path? = null
    val configFile: Path get() = _configFile ?: (baseDirectory / "node.conf")

    @Option(
            names = ["--on-unknown-config-keys"],
            description = ["How to behave on unknown node configuration. \${COMPLETION-CANDIDATES}"]
    )
    var unknownConfigKeysPolicy: UnknownConfigKeysPolicy = UnknownConfigKeysPolicy.FAIL

    @Option(
            names = ["-d", "--dev-mode"],
            description = ["Runs the node in development mode. Unsafe for production."]
    )
    var devMode: Boolean? = null

    open fun parseConfiguration(configuration: Config): Valid<NodeConfiguration> {
        val option = Configuration.Validation.Options(strict = unknownConfigKeysPolicy == UnknownConfigKeysPolicy.FAIL)
        return configuration.parseAsNodeConfiguration(option)
    }

    open fun rawConfiguration(): Validated<Config, ConfigException> {
        return try {
            valid(ConfigHelper.loadConfig(baseDirectory, configFile))
        } catch (e: ConfigException) {
            return invalid(e)
        }
    }

    fun copyFrom(other: SharedNodeCmdLineOptions) {
        baseDirectory = other.baseDirectory
        _configFile = other._configFile
        unknownConfigKeysPolicy= other.unknownConfigKeysPolicy
        devMode = other.devMode
    }

    fun logRawConfigurationErrors(errors: Set<ConfigException>) {
        if (errors.isNotEmpty()) {
            logger.error("There were error(s) while attempting to load the node configuration:")
        }
        errors.forEach { error ->
            when (error) {
                is ConfigException.IO -> logger.error(configFileNotFoundMessage(configFile))
                else -> logger.error(error.message)
            }
        }
    }

    private fun configFileNotFoundMessage(configFile: Path): String {
        return """
                Unable to load the node config file from '$configFile'.

                Try setting the --base-directory flag to change which directory the node
                is looking in, or use the --config-file flag to specify it explicitly.
            """.trimIndent()
    }
}

class InitialRegistrationCmdLineOptions : SharedNodeCmdLineOptions() {
    override fun parseConfiguration(configuration: Config): Valid<NodeConfiguration> {
        return super.parseConfiguration(configuration).doIfValid { config ->
            require(!config.devMode || config.devModeOptions?.allowCompatibilityZone == true) {
                "Cannot perform initial registration when 'devMode' is true, unless 'devModeOptions.allowCompatibilityZone' is also true."
            }
            require(config.compatibilityZoneURL != null || config.networkServices != null) {
                "compatibilityZoneURL or networkServices must be present in the node configuration file in registration mode."
            }
        }
    }
}

open class NodeCmdLineOptions : SharedNodeCmdLineOptions() {
    @Option(
            names = ["--sshd"],
            description = ["If set, enables SSH server for node administration."]
    )
    var sshdServer: Boolean = false

    @Option(
            names = ["--sshd-port"],
            description = ["The port to start the SSH server on, if enabled."]
    )
    var sshdServerPort: Int = 2222

    @Option(
            names = ["-n", "--no-local-shell"],
            description = ["Do not start the embedded shell locally."]
    )
    var noLocalShell: Boolean = false

    @Option(
            names = ["--just-generate-node-info"],
            description = ["DEPRECATED. Performs the node start-up tasks necessary to generate the nodeInfo file, saves it to disk, then exits."],
            hidden = true
    )
    var justGenerateNodeInfo: Boolean = false

    @Option(
            names = ["--just-generate-rpc-ssl-settings"],
            description = ["DEPRECATED. Generates the SSL key and trust stores for a secure RPC connection."],
            hidden = true
    )
    var justGenerateRpcSslCerts: Boolean = false

    @Option(
            names = ["--clear-network-map-cache"],
            description = ["DEPRECATED. Clears local copy of network map, on node startup it will be restored from server or file system."],
            hidden = true
    )
    var clearNetworkMapCache: Boolean = false

    @Option(
            names = ["--initial-registration"],
            description = ["DEPRECATED. Starts initial node registration with Corda network to obtain certificate from the permissioning server."],
            hidden = true
    )
    var isRegistration: Boolean = false

    @Option(
            names = ["-t", "--network-root-truststore"],
            description = ["DEPRECATED. Network root trust store obtained from network operator."],
            hidden = true
    )
    var networkRootTrustStorePathParameter: Path? = null

    @Option(
            names = ["-p", "--network-root-truststore-password"],
            description = ["DEPRECATED. Network root trust store password obtained from network operator."],
            hidden = true
    )
    var networkRootTrustStorePassword: String? = null

    override fun parseConfiguration(configuration: Config): Valid<NodeConfiguration> {
        return super.parseConfiguration(configuration).doIfValid { config ->
            if (isRegistration) {
                require(config.compatibilityZoneURL != null || config.networkServices != null) {
                    "compatibilityZoneURL or networkServices must be present in the node configuration file in registration mode."
                }
            }
        }
    }

    override fun rawConfiguration(): Validated<Config, ConfigException> {
        val configOverrides = mutableMapOf<String, Any>()
        configOverrides += "noLocalShell" to noLocalShell
        if (sshdServer) {
            configOverrides += "sshd" to mapOf("port" to sshdServerPort.toString())
        }
        devMode?.let {
            configOverrides += "devMode" to it
        }
        return try {
            valid(ConfigHelper.loadConfig(baseDirectory, configFile, configOverrides = ConfigFactory.parseMap(configOverrides)))
        } catch (e: ConfigException) {
            return invalid(e)
        }
    }
}


data class NodeRegistrationOption(val networkRootTrustStorePath: Path, val networkRootTrustStorePassword: String)
