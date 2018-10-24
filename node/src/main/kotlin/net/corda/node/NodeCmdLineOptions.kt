package net.corda.node

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.core.internal.div
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NodeConfigurationImpl
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

open class SharedNodeCmdLineOptions {
    @Option(
            names = ["-b", "--base-directory"],
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

    open fun loadConfig(): NodeConfiguration {
        return getRawConfig().parseAsNodeConfiguration(unknownConfigKeysPolicy::handle)
    }

    protected fun getRawConfig(): Config {
        val rawConfig = ConfigHelper.loadConfig(
                baseDirectory,
                configFile
        )
        if (devMode == true) {
            println("Config:\n${rawConfig.root().render(ConfigRenderOptions.defaults())}")
        }
        return rawConfig
    }

    fun copyFrom(other: SharedNodeCmdLineOptions) {
        baseDirectory = other.baseDirectory
        _configFile = other._configFile
        unknownConfigKeysPolicy= other.unknownConfigKeysPolicy
        devMode = other.devMode
    }
}

class InitialRegistrationCmdLineOptions : SharedNodeCmdLineOptions() {
    override fun loadConfig(): NodeConfiguration {
        return getRawConfig().parseAsNodeConfiguration(unknownConfigKeysPolicy::handle).also { config ->
            require(!config.devMode) { "Registration cannot occur in development mode" }
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

    override fun loadConfig(): NodeConfiguration {
        val rawConfig = ConfigHelper.loadConfig(
                baseDirectory,
                configFile,
                configOverrides = ConfigFactory.parseMap(mapOf("noLocalShell" to this.noLocalShell) +
                        if (sshdServer) mapOf("sshd" to mapOf("port" to sshdServerPort.toString())) else emptyMap<String, Any>() +
                                if (devMode != null) mapOf("devMode" to this.devMode) else emptyMap())
        )
        return rawConfig.parseAsNodeConfiguration(unknownConfigKeysPolicy::handle).also { config ->
            if (isRegistration) {
                require(!config.devMode) { "Registration cannot occur in development mode" }
                require(config.compatibilityZoneURL != null || config.networkServices != null) {
                    "compatibilityZoneURL or networkServices must be present in the node configuration file in registration mode."
                }
            }
        }
    }
}


data class NodeRegistrationOption(val networkRootTrustStorePath: Path, val networkRootTrustStorePassword: String)
