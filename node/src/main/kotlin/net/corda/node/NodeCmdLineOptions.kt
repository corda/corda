package net.corda.node

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.cliutils.ConfigFilePathArgsParser
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.utilities.Try
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.tools.shell.SSHDConfiguration
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

class NodeCmdLineOptions {
    @Option(
            names = ["-b", "--base-directory"],
            description = ["The node working directory where all the files are kept."]
    )
    var baseDirectory: Path = Paths.get(".")

    @Option(
            names = ["-f", "--config-file"],
            description = ["The path to the config file."]
    )
    var configFileArgument: Path? = null

    val configFile : Path
        get() {
            return if (configFileArgument == null) {
                baseDirectory / "node.conf"
            } else {
                configFileArgument!!
            }
        }

    @Option(
            names = ["-c", "--log-to-console"],
            description = ["If set, prints logging to the console as well as to a file."]
    )
    var logToConsole: Boolean = false

    @Option(
            names = ["--sshd"],
            description = ["If set, enables sshd server for node administration."]
    )
    var sshdServer: Boolean = false

    @Option(
            names = ["--sshd-port"],
            description = ["The port to start the sshd server on, if enabled."]
    )
    var sshdServerPort: Int = 2222

    @Option(
            names = ["-n", "--no-local-shell"],
            description = ["Do not start the embedded shell locally."]
    )
    var noLocalShell: Boolean = false

    @Option(
            names = ["--initial-registration"],
            description = ["Start initial node registration with Corda network to obtain certificate from the permissioning server."]
    )
    var isRegistration: Boolean = false


    @Option(
            names = ["-t", "--network-root-truststore"],
            description = ["Network root trust store obtained from network operator."]
    )
    var networkRootTrustStorePath = Paths.get("certificates") / "network-root-truststore.jks"

    @Option(
            names = ["-p", "--network-root-truststore-password"],
            description = ["Network root trust store password obtained from network operator."]
    )
    var networkRootTrustStorePassword: String? = null

    @Option(
            names = ["--on-unknown-config-keys"],
            description = ["Network root trust store password obtained from network operator. \${COMPLETION-CANDIDATES}"]
    )
    var unknownConfigKeysPolicy: UnknownConfigKeysPolicy = UnknownConfigKeysPolicy.FAIL

    @Option(
            names = ["-d", "--dev-mode"],
            description = ["Run the node in developer mode. Unsafe for production."]
    )
    var devMode: Boolean? = null

    @Option(
            names = ["--just-generate-node-info"],
            description = ["Perform the node start-up task necessary to generate its nodeInfo, save it to disk, then quit"]
    )
    var justGenerateNodeInfo: Boolean = false

    @Option(
            names = ["--just-generate-rpc-ssl-settings"],
            description = ["Generate the ssl keystore and truststore for a secure RPC connection."]
    )
    var justGenerateRpcSslCerts: Boolean = false

    @Option(
            names = ["--bootstrap-raft-cluster"],
            description = ["Bootstraps Raft cluster. The node forms a single node cluster (ignoring otherwise configured peer addresses), acting as a seed for other nodes to join the cluster."]
    )
    var bootstrapRaftCluster: Boolean = false

    @Option(
            names = ["--clear-network-map-cache"],
            description = ["Clears local copy of network map, on node startup it will be restored from server or file system."]
    )
    var clearNetworkMapCache: Boolean = false

    val nodeRegistrationOption : NodeRegistrationOption? by lazy {
        if (isRegistration) {
            requireNotNull(networkRootTrustStorePassword) { "Network root trust store password must be provided in registration mode using --network-root-truststore-password." }
            require(networkRootTrustStorePath.exists()) { "Network root trust store path: '$networkRootTrustStorePath' doesn't exist" }
            NodeRegistrationOption(networkRootTrustStorePath, networkRootTrustStorePassword!!)
        } else {
            null
        }
    }

    fun loadConfig(): Pair<Config, Try<NodeConfiguration>> {
        val rawConfig = ConfigHelper.loadConfig(
                baseDirectory,
                configFile,
                configOverrides = ConfigFactory.parseMap(mapOf("noLocalShell" to this.noLocalShell) +
                        if (sshdServer) mapOf("sshd" to mapOf("port" to sshdServerPort.toString())) else emptyMap<String, Any>() +
                        if (devMode != null) mapOf("devMode" to this.devMode) else emptyMap())
        )
        return rawConfig to Try.on {
            rawConfig.parseAsNodeConfiguration(unknownConfigKeysPolicy::handle).also { config ->
                if (nodeRegistrationOption != null) {
                    require(!config.devMode) { "registration cannot occur in devMode" }
                    require(config.compatibilityZoneURL != null || config.networkServices != null) {
                        "compatibilityZoneURL or networkServices must be present in the node configuration file in registration mode."
                    }
                }
            }
        }
    }
}

data class NodeRegistrationOption(val networkRootTrustStorePath: Path, val networkRootTrustStorePassword: String)
