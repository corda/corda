/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.webserver

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import net.corda.core.internal.div
import net.corda.core.utilities.loggerFor
import org.slf4j.event.Level
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

// NOTE: Do not use any logger in this class as args parsing is done before the logger is setup.
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
            .defaultsTo("web-server.conf")
    private val loggerLevel = optionParser
            .accepts("logging-level", "Enable logging at this level and higher")
            .withRequiredArg()
            .withValuesConvertedBy(object : EnumConverter<Level>(Level::class.java) {})
            .defaultsTo(Level.INFO)
    private val logToConsoleArg = optionParser.accepts("log-to-console", "If set, prints logging to the console as well as to a file.")
    private val helpArg = optionParser.accepts("help").forHelp()

    fun parse(vararg args: String): CmdLineOptions {
        val optionSet = optionParser.parse(*args)
        require(!optionSet.has(baseDirectoryArg) || !optionSet.has(configFileArg)) {
            "${baseDirectoryArg.options()[0]} and ${configFileArg.options()[0]} cannot be specified together"
        }
        val baseDirectory = Paths.get(optionSet.valueOf(baseDirectoryArg)).normalize().toAbsolutePath()
        val configFile = baseDirectory / optionSet.valueOf(configFileArg)
        val help = optionSet.has(helpArg)
        val loggingLevel = optionSet.valueOf(loggerLevel)
        val logToConsole = optionSet.has(logToConsoleArg)
        return CmdLineOptions(baseDirectory, configFile, help, loggingLevel, logToConsole)
    }

    fun printHelp(sink: PrintStream) = optionParser.printHelpOn(sink)
}

data class CmdLineOptions(val baseDirectory: Path,
                          val configFile: Path,
                          val help: Boolean,
                          val loggingLevel: Level,
                          val logToConsole: Boolean) {

    fun loadConfig(allowMissingConfig: Boolean = false, configOverrides: Map<String, Any?> = emptyMap()): Config {
        return loadConfig(baseDirectory, configFile, allowMissingConfig, configOverrides)
    }

    private fun loadConfig(baseDirectory: Path,
                           configFile: Path = baseDirectory / "node.conf",
                           allowMissingConfig: Boolean = false,
                           configOverrides: Map<String, Any?> = emptyMap()): Config {
        val parseOptions = ConfigParseOptions.defaults()
        val defaultConfig = ConfigFactory.parseResources("web-reference.conf", parseOptions.setAllowMissing(false))
        val appConfig = ConfigFactory.parseFile(configFile.toFile(), parseOptions.setAllowMissing(allowMissingConfig))
        val overrideConfig = ConfigFactory.parseMap(configOverrides + mapOf(
                // Add substitution values here
                "baseDirectory" to baseDirectory.toString())
        )
        val finalConfig = overrideConfig
                .withFallback(appConfig)
                .withFallback(defaultConfig)
                .resolve()
        val log = loggerFor<CmdLineOptions>() // I guess this is lazy so it happens after logging init.
        log.info("Config:\n${finalConfig.root().render(ConfigRenderOptions.defaults())}")
        return finalConfig
    }
}
