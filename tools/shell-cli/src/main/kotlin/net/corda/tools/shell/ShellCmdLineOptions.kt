package net.corda.tools.shell

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.div
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.parseAs
import net.corda.tools.shell.ShellConfiguration.Companion.COMMANDS_DIR
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths

class ShellCmdLineOptions {
    @Option(
            names = ["-f", "--config-file"],
            description = ["The path to the shell configuration file, used instead of providing the rest of command line options."]
    )
    var configFile: Path? = null

    @Option(
            names = ["-c", "--cordapp-directory"],
            description = ["The path to the directory containing CorDapp jars, CorDapps are required when starting flows."]
    )
    var cordappDirectory: Path? = null

    @Option(
            names = ["-o", "--commands-directory"],
            description = ["The path to the directory containing additional CrAsH shell commands."]
    )
    var commandsDirectory: Path? = null

    @Option(
            names = ["-a", "--host"],
            description = ["The host address of the Corda node."]
    )
    var host: String? = null

    @Option(
            names = ["-p", "--port"],
            description = ["The RPC port of the Corda node."]
    )
    var port: String? = null

    @Option(
            names = ["--user"],
            description = ["The RPC user name."]
    )
    var user: String? = null

    @Option(
            names = ["--password"],
            description = ["The RPC user password."]
    )
    var password: String? = null


    @Option(
            names = ["--sshd-port"],
            description = ["Enables SSH server for shell."]
    )
    var sshdPort: String? = null

    @Option(
            names = ["--sshd-hostkey-directory"],
            description = ["The directory with hostkey.pem file for SSH server."]
    )
    var sshdHostKeyDirectory: Path? = null

    @Option(
            names = ["--truststore-password"],
            description = ["The password to unlock the TrustStore file."]
    )
    var trustStorePassword: String? = null


    @Option(
            names = ["--truststore-file"],
            description = ["The path to the TrustStore file."]
    )
    var trustStoreFile: Path? = null


    @Option(
            names = ["--truststore-type"],
            description = ["The type of the TrustStore (e.g. JKS)."]
    )
    var trustStoreType: String? = null


    private fun toConfigFile(): Config {
        val cmdOpts = mutableMapOf<String, Any?>()

        commandsDirectory?.apply { cmdOpts["extensions.commands.path"] = this.toString() }
        cordappDirectory?.apply { cmdOpts["extensions.cordapps.path"] = this.toString() }
        user?.apply { cmdOpts["node.user"] = this }
        password?.apply { cmdOpts["node.password"] = this }
        host?.apply { cmdOpts["node.addresses.rpc.host"] = this }
        port?.apply { cmdOpts["node.addresses.rpc.port"] = this }
        trustStoreFile?.apply { cmdOpts["ssl.truststore.path"] = this.toString() }
        trustStorePassword?.apply { cmdOpts["ssl.truststore.password"] = this }
        trustStoreType?.apply { cmdOpts["ssl.truststore.type"] = this }
        sshdPort?.apply {
            cmdOpts["extensions.sshd.port"] = this
            cmdOpts["extensions.sshd.enabled"] = true
        }
        sshdHostKeyDirectory?.apply { cmdOpts["extensions.sshd.hostkeypath"] = this.toString() }

        return ConfigFactory.parseMap(cmdOpts)
    }

    /** Return configuration parsed from an optional config file (provided by the command line option)
     * and then overridden by the command line options */
    fun toConfig(): ShellConfiguration {
        val fileConfig = configFile?.let { ConfigFactory.parseFile(it.toFile()) }
                ?: ConfigFactory.empty()
        val typeSafeConfig = toConfigFile().withFallback(fileConfig).resolve()
        val shellConfigFile = typeSafeConfig.parseAs<ShellConfigurationFile.ShellConfigFile>()
        return shellConfigFile.toShellConfiguration()
    }
}

/** Object representation of Shell configuration file */
private class ShellConfigurationFile {
    data class Rpc(
            val host: String,
            val port: Int)

    data class Addresses(
            val rpc: Rpc
    )

    data class Node(
            val addresses: Addresses,
            val user: String?,
            val password: String?
    )

    data class Cordapps(
            val path: String
    )

    data class Sshd(
            val enabled: Boolean,
            val port: Int,
            val hostkeypath: String?
    )

    data class Commands(
            val path: String
    )

    data class Extensions(
            val cordapps: Cordapps?,
            val sshd: Sshd?,
            val commands: Commands?
    )

    data class KeyStore(
            val path: String,
            val type: String = "JKS",
            val password: String
    )

    data class Ssl(
            val truststore: KeyStore
    )

    data class ShellConfigFile(
            val node: Node,
            val extensions: Extensions?,
            val ssl: Ssl?
    ) {
        fun toShellConfiguration(): ShellConfiguration {

            val sslOptions =
                    ssl?.let {
                        ClientRpcSslOptions(
                                trustStorePath = Paths.get(it.truststore.path),
                                trustStorePassword = it.truststore.password)
                    }

            return ShellConfiguration(
                    commandsDirectory = extensions?.commands?.let { Paths.get(it.path) } ?: Paths.get(".")
                    / COMMANDS_DIR,
                    cordappsDirectory = extensions?.cordapps?.let { Paths.get(it.path) },
                    user = node.user ?: "",
                    password = node.password ?: "",
                    hostAndPort = NetworkHostAndPort(node.addresses.rpc.host, node.addresses.rpc.port),
                    ssl = sslOptions,
                    sshdPort = extensions?.sshd?.let { if (it.enabled) it.port else null },
                    sshHostKeyDirectory = extensions?.sshd?.let { if (it.enabled && it.hostkeypath != null) Paths.get(it.hostkeypath) else null })
        }
    }
}
