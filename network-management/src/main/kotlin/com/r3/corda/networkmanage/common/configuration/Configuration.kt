package com.r3.corda.networkmanage.common.configuration

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import joptsimple.OptionParser
import joptsimple.util.PathConverter
import joptsimple.util.PathProperties
import java.nio.file.Path

/**
 * Parses key generator command line options.
 */
fun parseCommandLine(vararg args: String): Path {
    val optionParser = OptionParser()
    val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .required()
            .describedAs("filepath")
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))
    val helpOption = optionParser.acceptsAll(listOf("h", "help"), "show help").forHelp()

    val optionSet = optionParser.parse(*args)
    // Print help and exit on help option or if there are missing options.
    if (optionSet.has(helpOption) || !optionSet.has(configFileArg)) {
        throw ShowHelpException(optionParser)
    }
    return optionSet.valueOf(configFileArg).toAbsolutePath()
}