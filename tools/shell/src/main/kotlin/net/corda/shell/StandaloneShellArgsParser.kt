package net.corda.shell

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.parseAs
import org.slf4j.event.Level
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

// NOTE: Do not use any logger in this class as args parsing is done before the logger is setup.
class RemoteShellArgsParser {
    private val optionParser = OptionParser()

    private val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withOptionalArg()
    private val hostArg = optionParser
            .acceptsAll(listOf("h","host"), "The host of the Corda node.")
            .withRequiredArg()
    private val portArg = optionParser
            .acceptsAll(listOf("p","port"), "The port of the Corda node.")
            .withRequiredArg()
    private val userArg = optionParser
            .accepts("user", "The RPC user name.")
            .withRequiredArg()
            .defaultsTo("")
    private val passwordArg = optionParser
            .accepts("password", "The RPC user password.")
            .withOptionalArg()
    private val baseDirectoryArg = optionParser
            .accepts("base-directory", "The shell working directory where all the files are kept.")
            .withRequiredArg()
            .defaultsTo(".")
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

    fun parse(vararg args: String): CmdLineOptions {
        val optionSet = optionParser.parse(*args)
        require((optionSet.has(hostArg) && optionSet.has(portArg))
                || optionSet.has(helpArg)
                || optionSet.has(configFileArg) ) {
            "Require 'host' and 'port' option"
        }

        val configFile = optionSet.valueOf(configFileArg)
        val host = optionSet.valueOf(hostArg)
        val port = optionSet.valueOf(portArg)
        val user = optionSet.valueOf(userArg)
        val password = optionSet.valueOf(passwordArg)
        val baseDirectory = Paths.get(optionSet.valueOf(baseDirectoryArg)).normalize().toAbsolutePath()
        val help = optionSet.has(helpArg)
        val loggingLevel = optionSet.valueOf(loggerLevel)
        val sshdPort = optionSet.valueOf(sshdPortArg)
        val keyStorePassword = optionSet.valueOf(keyStorePasswordArg)
        val trustStorePassword = optionSet.valueOf(trustStorePasswordArg)
        return CmdLineOptions(configFile,
                host,
                port,
                user,
                password,
                baseDirectory,
                help,
                loggingLevel,
                sshdPort,
                keyStorePassword,
                trustStorePassword,
                false)
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}
// TODO Simon restructure format of config file
data class CmdLineOptions(val configFile: String?,
                          val host: String?,
                          val port: String?,
                          val user: String?,
                          val password: String?,
                          val baseDirectory: Path?,
                          val help: Boolean,
                          val loggingLevel: Level,
                          val sshPort: String?,
                          val keyStorePassword: String?,
                          val trustStorePassword: String?,
                          val noLocalShell: Boolean?) {
    fun toConfig(): ShellConfiguration {
        return if (configFile.isNullOrBlank()) parseConfig() else loadConfig()
    }

    private fun parseConfig(): ShellConfiguration {
        val cmdOpts = mutableMapOf<String,Any?>()
        if (host != null && port != null) {
            cmdOpts["hostAndPort"] = "$host:$port"
        }
        user?.apply { cmdOpts["user"] = user }
        password?.apply { cmdOpts["password"] = password }
        baseDirectory?.apply { cmdOpts["baseDirectory"] = baseDirectory.toString() }
        noLocalShell?.apply { cmdOpts["noLocalShell"] = noLocalShell }
        sshPort?.apply { cmdOpts["sshd"] = sshPort }
        if (keyStorePassword != null && trustStorePassword != null && baseDirectory != null) {
            cmdOpts["ssl"] = mapOf( "certificatesDirectory" to (baseDirectory / "certificates").toString(),
                    "keyStorePassword" to keyStorePassword,
                    "trustStorePassword" to trustStorePassword)
        }
        return ConfigFactory.parseMap(cmdOpts).resolve().parseAs()
    }

    private fun loadConfig(): ShellConfiguration {
        data class SslConfigurationFile(val keyStorePassword: String, val trustStorePassword: String)

        data class ShellConfigurationFile(
                val configPath: Path?,
                val baseDirectory: Path,
                val user: String,
                val password: String?,
                val hostAndPort: NetworkHostAndPort,
                val ssl: SslConfigurationFile?,
                val sshdPort: Int?) {
            fun toShellOptions() : ShellConfiguration {
                val ssl = ShellSslOptions(baseDirectory / "certificates",
                        ssl?.keyStorePassword ?:"", ssl?.trustStorePassword ?: "")
                return ShellConfiguration(baseDirectory, user, password, hostAndPort, ssl, sshdPort, noLocalShell = false)
            }
        }

        val parseOptions = ConfigParseOptions.defaults()
        val config = ConfigFactory.parseFile(Paths.get(configFile).toFile(), parseOptions.setAllowMissing(false))
                .resolve().parseAs<ShellConfigurationFile>()
        return config.toShellOptions()
    }
}
