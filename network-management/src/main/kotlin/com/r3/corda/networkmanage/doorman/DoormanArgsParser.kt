package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import joptsimple.util.PathConverter
import joptsimple.util.PathProperties
import net.corda.nodeapi.internal.config.parseAs
import java.nio.file.Path

class DoormanArgsParser {
    private val optionParser = OptionParser()
    private val helpOption = optionParser.acceptsAll(listOf("h", "help"), "show help").forHelp()
    private val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))
            .required()
    private val modeArg = optionParser
            .accepts("mode", "Set the mode of this application")
            .withRequiredArg()
            .withValuesConvertedBy(object : EnumConverter<Mode>(Mode::class.java) {})
            .defaultsTo(Mode.DOORMAN)
    private val updateNetworkParametersArg = optionParser
            .accepts("update-network-parameters", "Update network parameters file. Currently only network parameters initialisation is supported.")
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))
    private val trustStorePasswordArg = optionParser
            .accepts("trust-store-password", "Password for the generated network root trust store. Only required when operating in ${Mode.ROOT_KEYGEN} mode.")
            .withRequiredArg()

    fun parse(vararg args: String): DoormanCmdLineOptions {
        val optionSet = optionParser.parse(*args)
        if (optionSet.has(helpOption)) {
            throw ShowHelpException(optionParser)
        }
        val configFile = optionSet.valueOf(configFileArg)
        val mode = optionSet.valueOf(modeArg)
        val networkParametersFile = optionSet.valueOf(updateNetworkParametersArg)
        val trustStorePassword = optionSet.valueOf(trustStorePasswordArg)
        return DoormanCmdLineOptions(configFile, mode, networkParametersFile, trustStorePassword)
    }
}

data class DoormanCmdLineOptions(val configFile: Path, val mode: Mode, val networkParametersFile: Path?, val trustStorePassword: String?) {
    init {
        // Make sure trust store password is only specified in root keygen mode.
        if (mode != Mode.ROOT_KEYGEN) {
            require(trustStorePassword == null) { "Trust store password should not be specified in this mode." }
        }
    }
}
