package com.r3.corda.networkmanage.common.configuration

import com.r3.corda.networkmanage.common.utils.ArgsParser
import joptsimple.OptionSet
import joptsimple.util.PathConverter
import joptsimple.util.PathProperties
import java.nio.file.Path

/**
 * Parses key generator command line options.
 */
class ConfigFilePathArgsParser : ArgsParser<Path>() {
    private val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .required()
            .describedAs("filepath")
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))

    override fun parse(optionSet: OptionSet): Path {
        return optionSet.valueOf(configFileArg).toAbsolutePath()
    }
}