package com.r3.corda.doorman

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import kotlin.system.exitProcess

object OptionParserHelper {
    fun Array<String>.toConfigWithOptions(options: OptionParser.() -> Unit): Config {
        val parser = OptionParser()
        val helpOption = parser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp();
        options(parser)
        val optionSet = parser.parse(*this)
        if (optionSet.has(helpOption)) {
            parser.printHelpOn(System.out)
            exitProcess(0)
        }

        return ConfigFactory.parseMap(parser.recognizedOptions().mapValues {
            val optionSpec = it.value
            if (optionSpec is ArgumentAcceptingOptionSpec<*> && !optionSpec.requiresArgument() && optionSet.has(optionSpec)) true else optionSpec.value(optionSet)
        }.filterValues { it != null })
    }
}