package net.corda.shell

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.parseAs
import org.slf4j.event.Level
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

// NOTE: Do not use any logger in this class as args parsing is done before the logger is setup.
class CommandLineOptionParser {
    private val optionParser = OptionParser()

    private val configFileArg = optionParser
            .accepts("config-file", "The path to the shell configuration file.")
            .withOptionalArg()
    private val shellDirectoryArg = optionParser
            .accepts("shell-directory", "The CrAsH shell working directory.")
            .withRequiredArg()
            .defaultsTo(".")
    private val cordappsDirectoryArg = optionParser
            .accepts("cordpass-directory", "The path to directory containing Cordapps jars, Cordapps are require when starting flows.")
            .withOptionalArg()
    private val hostArg = optionParser
            .acceptsAll(listOf("h","host"), "The host of the Corda node.")
            .withRequiredArg()
    private val portArg = optionParser
            .acceptsAll(listOf("p","port"), "The port of the Corda node.")
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
    private val helpArg = optionParser
            .accepts("help")
            .forHelp()
    private val keyStorePasswordArg = optionParser
            .accepts("keystore-password", "The password to unlock the KeyStore file.")
            .withOptionalArg()
    private val trustStorePasswordArg = optionParser
            .accepts("truststore-password", "The password to unlock the TrustStore file.")
            .withOptionalArg()
    private val keyStoreDirArg = optionParser
            .accepts("keystore-file", "The KeyStore file.")
            .withOptionalArg()
    private val trustStoreDirArg = optionParser
            .accepts("truststore-file", "The TrustStore file.")
            .withOptionalArg()

    fun parse(vararg args: String): CommandLineOptions {
        val optionSet = optionParser.parse(*args)
        return CommandLineOptions(
                configFile = optionSet.valueOf(configFileArg),
                host = optionSet.valueOf(hostArg),
                port = optionSet.valueOf(portArg),
                user = optionSet.valueOf(userArg),
                password = optionSet.valueOf(passwordArg),
                shellDirectory = Paths.get(optionSet.valueOf(shellDirectoryArg)).normalize().toAbsolutePath(),
                cordappsDirectory = (optionSet.valueOf(cordappsDirectoryArg))?.let { Paths.get(optionSet.valueOf(cordappsDirectoryArg)).normalize().toAbsolutePath() },
                help = optionSet.has(helpArg),
                loggingLevel = optionSet.valueOf(loggerLevel),
                sshdPort = optionSet.valueOf(sshdPortArg),
                keyStorePassword = optionSet.valueOf(keyStorePasswordArg),
                trustStorePassword = optionSet.valueOf(trustStorePasswordArg),
                keyStoreFile = (optionSet.valueOf(keyStoreDirArg))?.let { Paths.get(optionSet.valueOf(keyStoreDirArg)).normalize().toAbsolutePath() },
                trustStoreFile = (optionSet.valueOf(trustStoreDirArg))?.let { Paths.get(optionSet.valueOf(trustStoreDirArg)).normalize().toAbsolutePath() })
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}

data class CommandLineOptions(val configFile: String?,
                          val shellDirectory: Path?,
                          val cordappsDirectory: Path?,
                          val host: String?,
                          val port: String?,
                          val user: String?,
                          val password: String?,
                          val help: Boolean,
                          val loggingLevel: Level,
                          val sshdPort: String?,
                          val keyStorePassword: String?,
                          val trustStorePassword: String?,
                          val keyStoreFile: Path?,
                          val trustStoreFile: Path?) {

    private fun toConfigFile(): Config {
        val cmdOpts = mutableMapOf<String, Any?>()

        shellDirectory?.apply { cmdOpts["shell.workDir"] = shellDirectory.toString() }
        cordappsDirectory?.apply { cmdOpts["extensions.cordapps.path"] = cordappsDirectory.toString() }
        user?.apply { cmdOpts["user"] = user }
        password?.apply { cmdOpts["password"] = password }
        host?.apply { cmdOpts["node.addresses.rpc.host"] = host }
        port?.apply { cmdOpts["node.addresses.rpc.port"] = port }
        shellDirectory?.apply { cmdOpts["shell.workDir"] = shellDirectory.toString() }
        keyStoreFile?.apply { cmdOpts["ssl.keystore.path"] = keyStoreFile.toString() }
        keyStorePassword?.apply { cmdOpts["ssl.keystore.password"] = keyStorePassword }
        trustStoreFile?.apply { cmdOpts["ssl.truststore.path"] = trustStoreFile.toString() }
        trustStorePassword?.apply { cmdOpts["ssl.truststore.password"] = trustStorePassword }
        sshdPort?.apply {
            cmdOpts["extensions.sshd.port"] = sshdPort
            cmdOpts["extensions.sshd.enabled"] = true
        }

        return ConfigFactory.parseMap(cmdOpts)
    }

    fun toConfig(): ShellConfiguration {

        val fileConfig = configFile?.let{ ConfigFactory.parseFile(Paths.get(configFile).toFile()) } ?: ConfigFactory.empty()
        val typeSafeConfig = toConfigFile().withFallback(fileConfig).resolve()
        val shellConfigFile = typeSafeConfig.parseAs<ShellConfigurationFile.ShellConfigFile>()
        return shellConfigFile.toShellConfiguration()
    }
}

/** Object representation of Shell configuration file */
class ShellConfigurationFile {
    data class Rpc(
            val host: String,
            val port: Int)

    data class Addresses(
            val rpc: Rpc
    )
    data class Node(
            val addresses: Addresses
    )
    data class Shell(
            val workDir: String
    )
    data class Cordapps(
            val path: String
    )
    data class Sshd(
            val enabled: Boolean,
            val port: Int
    )
    data class Extensions(
            val cordapps: Cordapps,
            val sshd: Sshd
    )
    data class KeyStore(
            val path : String,
            val type: String = "JKS",
            val password: String
    )
    data class Ssl(
            val keystore : KeyStore,
            val truststore: KeyStore
    )
    data class ShellConfigFile (
            val node: Node,
            val shell: Shell,
            val extensions: Extensions?,
            val ssl: Ssl?,
            val user: String?,
            val password: String?
    ) {
        fun toShellConfiguration(): ShellConfiguration {

            val sslOptions =
                   ssl?.let { ShellSslOptions(
                            sslKeystore = Paths.get(it.keystore.path),
                            keyStorePassword = it.keystore.password,
                            trustStoreFile = Paths.get(it.truststore.path),
                            trustStorePassword = it.truststore.password) }

            return ShellConfiguration(
                    shellDirectory = Paths.get(shell.workDir),
                    cordappsDirectory = extensions?.let { Paths.get(extensions.cordapps.path) },
                    user = user,
                    password = password,
                    hostAndPort = NetworkHostAndPort(node.addresses.rpc.host, node.addresses.rpc.port),
                    ssl = sslOptions,
                    sshdPort = extensions?.sshd?.let { if (it.enabled)it.port else null })
        }
    }
}
