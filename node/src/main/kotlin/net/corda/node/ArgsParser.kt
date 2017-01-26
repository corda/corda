package net.corda.node

import com.typesafe.config.Config
import joptsimple.OptionParser
import net.corda.core.div
import net.corda.node.services.config.ConfigHelper
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

class ArgsParser {
    private val optionParser = OptionParser()
    // The intent of allowing a command line configurable directory and config path is to allow deployment flexibility.
    // Other general configuration should live inside the config file unless we regularly need temporary overrides on the command line
    private val baseDirectoryArg = optionParser
            .accepts("base-directory", "The node working directory where all the files are kept")
            .withRequiredArg()
            .defaultsTo(".")
    private val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .defaultsTo("node.conf")
    private val logToConsoleArg = optionParser.accepts("log-to-console", "If set, prints logging to the console as well as to a file.")
    private val helpArg = optionParser.accepts("help").forHelp()

    fun parse(vararg args: String): CmdLineOptions {
        val optionSet = optionParser.parse(*args)
        require(!optionSet.has(baseDirectoryArg) || !optionSet.has(configFileArg)) {
            "${baseDirectoryArg.options()[0]} and ${configFileArg.options()[0]} cannot be specified together"
        }
        val baseDirectory = Paths.get(optionSet.valueOf(baseDirectoryArg)).normalize().toAbsolutePath()
        val configFile = baseDirectory / optionSet.valueOf(configFileArg)
        return CmdLineOptions(baseDirectory, configFile, optionSet.has(helpArg), optionSet.has(logToConsoleArg))
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}

data class CmdLineOptions(val baseDirectory: Path, val configFile: Path?, val help: Boolean, val logToConsole: Boolean) {
    fun loadConfig(allowMissingConfig: Boolean = false, configOverrides: Map<String, Any?> = emptyMap()): Config {
        return ConfigHelper.loadConfig(baseDirectory, configFile, allowMissingConfig, configOverrides)
    }
}