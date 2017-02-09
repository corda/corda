package com.r3.corda.doorman

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import kotlin.system.exitProcess

/**
 * Convert commandline arguments to [Config] object will allow us to use kotlin delegate with [ConfigHelper].
 */
object OptionParserHelper {
    fun Array<String>.toConfigWithOptions(registerOptions: OptionParser.() -> Unit): Config {
        val parser = OptionParser()
        val helpOption = parser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp();
        registerOptions(parser)
        val optionSet = parser.parse(*this)
        // Print help and exit on help option.
        if (optionSet.has(helpOption)) {
            parser.printHelpOn(System.out)
            exitProcess(0)
        }
        // Convert all command line options to Config.
        return ConfigFactory.parseMap(parser.recognizedOptions().mapValues {
            val optionSpec = it.value
            if (optionSpec is ArgumentAcceptingOptionSpec<*> && !optionSpec.requiresArgument() && optionSet.has(optionSpec)) true else optionSpec.value(optionSet)
        }.filterValues { it != null })
    }
}