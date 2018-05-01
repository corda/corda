package net.corda.tools.shell

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.parseAs
import net.corda.tools.shell.ShellConfiguration.Companion.COMMANDS_DIR
import org.slf4j.event.Level
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

// NOTE: Do not use any logger in this class as args parsing is done before the logger is setup.
class CommandLineOptionParser {
    private val optionParser = OptionParser()

    private val configFileArg = optionParser
            .accepts("config-file", "The path to the shell configuration file, used instead of providing the rest of command line options.")
            .withOptionalArg()
    private val cordappsDirectoryArg = optionParser
            .accepts("cordpass-directory", "The path to directory containing Cordapps jars, Cordapps are require when starting flows.")
            .withOptionalArg()
    private val commandsDirectoryArg = optionParser
            .accepts("commands-directory", "The directory with additional CrAsH shell commands.")
            .withOptionalArg()
    private val hostArg = optionParser
            .acceptsAll(listOf("h", "host"), "The host of the Corda node.")
            .withRequiredArg()
    private val portArg = optionParser
            .acceptsAll(listOf("p", "port"), "The port of the Corda node.")
            .withRequiredArg()
    private val userArg = optionParser
            .accepts("user", "The RPC user name.")
            .withOptionalArg()
    private val passwordArg = optionParser
            .accepts("password", "The RPC user password.")
            .withOptionalArg()
    private val loggerLevel = optionParser
            .accepts("logging-level", "Enable logging at this level and higher.")
            .withRequiredArg()
            .withValuesConvertedBy(object : EnumConverter<Level>(Level::class.java) {})
            .defaultsTo(Level.INFO)
    private val sshdPortArg = optionParser
            .accepts("sshd-port", "Enables SSH server for shell.")
            .withOptionalArg()
    private val sshdHostKeyDirectoryArg = optionParser
            .accepts("sshd-hostkey-directory", "The directory with hostkey.pem file for SSH server.")
            .withOptionalArg()
    private val helpArg = optionParser
            .accepts("help")
            .forHelp()
    private val keyStorePasswordArg = optionParser
            .accepts("keystore-password", "The password to unlock the KeyStore file.")
            .withOptionalArg()
    private val keyStoreDirArg = optionParser
            .accepts("keystore-file", "The path to the KeyStore file.")
            .withOptionalArg()
    private val keyStoreTypeArg = optionParser
            .accepts("keystore-type", "The type of the KeyStore (e.g. JKS).")
            .withOptionalArg()
    private val trustStorePasswordArg = optionParser
            .accepts("truststore-password", "The password to unlock the TrustStore file.")
            .withOptionalArg()
    private val trustStoreDirArg = optionParser
            .accepts("truststore-file", "The path to the TrustStore file.")
            .withOptionalArg()
    private val trustStoreTypeArg = optionParser
            .accepts("truststore-type", "The type of the TrustStore (e.g. JKS).")
            .withOptionalArg()

    fun parse(vararg args: String): CommandLineOptions {
        val optionSet = optionParser.parse(*args)
        return CommandLineOptions(
                configFile = optionSet.valueOf(configFileArg),
                host = optionSet.valueOf(hostArg),
                port = optionSet.valueOf(portArg),
                user = optionSet.valueOf(userArg),
                password = optionSet.valueOf(passwordArg),
                commandsDirectory = (optionSet.valueOf(commandsDirectoryArg))?.let { Paths.get(it).normalize().toAbsolutePath() },
                cordappsDirectory = (optionSet.valueOf(cordappsDirectoryArg))?.let { Paths.get(it).normalize().toAbsolutePath() },
                help = optionSet.has(helpArg),
                loggingLevel = optionSet.valueOf(loggerLevel),
                sshdPort = optionSet.valueOf(sshdPortArg),
                sshdHostKeyDirectory = (optionSet.valueOf(sshdHostKeyDirectoryArg))?.let { Paths.get(it).normalize().toAbsolutePath() },
                keyStorePassword = optionSet.valueOf(keyStorePasswordArg),
                trustStorePassword = optionSet.valueOf(trustStorePasswordArg),
                keyStoreFile = (optionSet.valueOf(keyStoreDirArg))?.let { Paths.get(it).normalize().toAbsolutePath() },
                trustStoreFile = (optionSet.valueOf(trustStoreDirArg))?.let { Paths.get(it).normalize().toAbsolutePath() },
                keyStoreType = optionSet.valueOf(keyStoreTypeArg),
                trustStoreType = optionSet.valueOf(trustStoreTypeArg))
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}

data class CommandLineOptions(val configFile: String?,
                              val commandsDirectory: Path?,
                              val cordappsDirectory: Path?,
                              val host: String?,
                              val port: String?,
                              val user: String?,
                              val password: String?,
                              val help: Boolean,
                              val loggingLevel: Level,
                              val sshdPort: String?,
                              val sshdHostKeyDirectory: Path?,
                              val keyStorePassword: String?,
                              val trustStorePassword: String?,
                              val keyStoreFile: Path?,
                              val trustStoreFile: Path?,
                              val keyStoreType: String?,
                              val trustStoreType: String?) {

    private fun toConfigFile(): Config {
        val cmdOpts = mutableMapOf<String, Any?>()

        commandsDirectory?.apply { cmdOpts["extensions.commands.path"] = this.toString() }
        cordappsDirectory?.apply { cmdOpts["extensions.cordapps.path"] = this.toString() }
        user?.apply { cmdOpts["node.user"] = this }
        password?.apply { cmdOpts["node.password"] = this }
        host?.apply { cmdOpts["node.addresses.rpc.host"] = this }
        port?.apply { cmdOpts["node.addresses.rpc.port"] = this }
        keyStoreFile?.apply { cmdOpts["ssl.keystore.path"] = this.toString() }
        keyStorePassword?.apply { cmdOpts["ssl.keystore.password"] = this }
        keyStoreType?.apply { cmdOpts["ssl.keystore.type"] = this }
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
        val fileConfig = configFile?.let { ConfigFactory.parseFile(Paths.get(configFile).toFile()) }
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
            val cordapps: Cordapps,
            val sshd: Sshd,
            val commands: Commands?
    )

    data class KeyStore(
            val path: String,
            val type: String,
            val password: String
    )

    data class Ssl(
            val keystore: KeyStore,
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
                        ShellSslOptions(
                                sslKeystore = Paths.get(it.keystore.path),
                                keyStorePassword = it.keystore.password,
                                trustStoreFile = Paths.get(it.truststore.path),
                                trustStorePassword = it.truststore.password,
                                crlCheckSoftFail = true)
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
