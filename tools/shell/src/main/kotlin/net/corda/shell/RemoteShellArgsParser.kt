package net.corda.shell

import com.typesafe.config.ConfigFactory
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import net.corda.nodeapi.internal.config.parseAs
import org.slf4j.event.Level
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

// NOTE: Do not use any logger in this class as args parsing is done before the logger is setup.
class RemoteShellArgsParser {
    private val optionParser = OptionParser()
    // The intent of allowing a command line configurable directory and config path is to allow deployment flexibility.
    // Other general configuration should live inside the config file unless we regularly need temporary overrides on the command line
    private val hostArg = optionParser
            .acceptsAll(listOf("h","host"), "The node host to be connected to.")
            .withRequiredArg()
    private val portArg = optionParser
            .acceptsAll(listOf("p","port"), "The node port to be connected to.")
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
    private val sshdServerArg = optionParser
            .accepts("sshd", "Enables SSHD server for remote shell.")
            .withOptionalArg()
    private val helpArg = optionParser.accepts("help").forHelp()

    fun parse(vararg args: String): CmdLineOptions {
        val optionSet = optionParser.parse(*args)
        require((optionSet.has(hostArg) && optionSet.has(portArg)) || optionSet.has(helpArg)) {
            "Require 'host' and 'port' option"
        }
        val host = optionSet.valueOf(hostArg)
        val port = optionSet.valueOf(portArg)
        val user = optionSet.valueOf(userArg)
        val password = optionSet.valueOf(passwordArg)
        val baseDirectory = Paths.get(optionSet.valueOf(baseDirectoryArg)).normalize().toAbsolutePath()
        val help = optionSet.has(helpArg)
        val loggingLevel = optionSet.valueOf(loggerLevel)
        val sshdServer = optionSet.valueOf(sshdServerArg)
        return CmdLineOptions(host,
                port,
                user,
                password,
                baseDirectory,
                help,
                loggingLevel,
                false,
                sshdServer)
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}

data class CmdLineOptions(val host: String?,
                          val port: String?,
                          val user: String?,
                          val password: String?,
                          val baseDirectory: Path?,
                          val help: Boolean,
                          val loggingLevel: Level,
                          val noLocalShell: Boolean?,
                          val sshdServer: String?) {
    fun toConfig(): ShellConfiguration {
        val cmdOpts = mutableMapOf<String,Any?>()
        if (host != null && port != null) {cmdOpts["hostAndPort"] = "$host:$port" }
        user?.apply { cmdOpts["user"] = user }
        password?.apply { cmdOpts["password"] = password }
        baseDirectory?.apply { cmdOpts["baseDirectory"] = baseDirectory.toString() }
        noLocalShell?.apply { cmdOpts["noLocalShell"] = noLocalShell }
        sshdServer?.apply { cmdOpts["sshd"] = sshdServer }
        return  ConfigFactory.parseMap(cmdOpts).resolve().parseAs()
    }
}
