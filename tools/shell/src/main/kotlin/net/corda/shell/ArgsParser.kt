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
class ArgsParser {
    private val optionParser = OptionParser()
    // The intent of allowing a command line configurable directory and config path is to allow deployment flexibility.
    // Other general configuration should live inside the config file unless we regularly need temporary overrides on the command line
    private val hostAndPortArg = optionParser
            .acceptsAll(listOf("h","host"), "The node working directory where all the files are kept")
            .withRequiredArg()
    private val userArg = optionParser
            .accepts("user-login", "The node working directory where all the files are kept")
            .withRequiredArg()
            .defaultsTo("")
    private val baseDirectoryArg = optionParser
            .accepts("base-directory", "The node working directory where all the files are kept")
            .withRequiredArg()
            .defaultsTo(".")
    private val loggerLevel = optionParser
            .accepts("logging-level", "Enable logging at this level and higher")
            .withRequiredArg()
            .withValuesConvertedBy(object : EnumConverter<Level>(Level::class.java) {})
            .defaultsTo(Level.INFO)
    private val sshdServerArg = optionParser
            .accepts("sshd", "Enables SSHD server for node administration.")
            .withOptionalArg()
    private val noLocalShellArg = optionParser.accepts("no-local-shell", "Do not start the embedded shell locally.")
    private val helpArg = optionParser.accepts("help").forHelp()

    fun parse(vararg args: String): CmdLineOptions {
        val optionSet = optionParser.parse(*args)
        require(optionSet.has(hostAndPortArg)) {
            "Require 'host' option"
        }
        val hostAndPort = optionSet.valueOf(hostAndPortArg)
        val user = optionSet.valueOf(userArg)
        val baseDirectory = Paths.get(optionSet.valueOf(baseDirectoryArg)).normalize().toAbsolutePath()
        val help = optionSet.has(helpArg)
        val loggingLevel = optionSet.valueOf(loggerLevel)
        val noLocalShell = optionSet.has(noLocalShellArg)
        val sshdServer = optionSet.valueOf(sshdServerArg)
        return CmdLineOptions(hostAndPort,
                user,
                baseDirectory,
                help,
                loggingLevel,
                noLocalShell,
                sshdServer)
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}

data class CmdLineOptions(val hostAndPort: String?,
                          val user: String?,
                          val baseDirectory: Path?,
                          val help: Boolean,
                          val loggingLevel: Level, //TODO
                          val noLocalShell: Boolean?,
                          val sshdServer: String?) {
    fun toConfig(): ShellConfiguration {
        val cmdOpts = mutableMapOf<String,Any?>()
        hostAndPort?.apply { cmdOpts["hostAndPort"] = hostAndPort }
        user?.apply { cmdOpts["user"] = user }
        baseDirectory?.apply { cmdOpts["baseDirectory"] = baseDirectory.toString() }
        noLocalShell?.apply { cmdOpts["noLocalShell"] = noLocalShell }
        sshdServer?.apply { cmdOpts["sshd"] = sshdServer }
        return  ConfigFactory.parseMap(cmdOpts).resolve().parseAs()
    }
}
