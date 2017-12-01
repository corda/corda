package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.OptionParser
import joptsimple.util.EnumConverter
import net.corda.core.internal.isRegularFile
import net.corda.nodeapi.config.parseAs
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

data class DoormanParameters(// TODO Create a localSigning sub-config and put that there
                             val keystorePassword: String?,
                             // TODO Should be part of a localSigning sub-config
                             val caPrivateKeyPassword: String?,
                             // TODO Should be part of a localSigning sub-config
                             val rootKeystorePassword: String?,
                             // TODO Should be part of a localSigning sub-config
                             val rootPrivateKeyPassword: String?,
                             val host: String,
                             val port: Int,
                             val dataSourceProperties: Properties,
                             val approveAll: Boolean = false,
                             val databaseProperties: Properties? = null,
                             val jiraConfig: JiraConfig? = null,
                             // TODO Should be part of a localSigning sub-config
                             val keystorePath: Path? = null,
                             // TODO Should be part of a localSigning sub-config
                             val rootStorePath: Path? = null,
                             // TODO Change these to Duration in the future
                             val approveInterval: Long = DEFAULT_APPROVE_INTERVAL,
                             val signInterval: Long = DEFAULT_SIGN_INTERVAL
) {
    enum class Mode {
        DOORMAN, CA_KEYGEN, ROOT_KEYGEN
    }

    data class JiraConfig(
            val address: String,
            val projectCode: String,
            val username: String,
            val password: String,
            val doneTransitionCode: Int
    )

    companion object {
        val DEFAULT_APPROVE_INTERVAL = 5L // seconds
        val DEFAULT_SIGN_INTERVAL = 5L // seconds
    }
}

data class CommandLineOptions(val configFile: Path,
                              val updateNetworkParametersFile: Path?,
                              val mode: DoormanParameters.Mode) {
    init {
        check(configFile.isRegularFile()) { "Config file $configFile does not exist" }
        if (updateNetworkParametersFile != null) {
            check(updateNetworkParametersFile.isRegularFile()) { "Update network parameters file $updateNetworkParametersFile does not exist" }
        }
    }
}

/**
 * Parses the doorman command line options.
 */
fun parseCommandLine(vararg args: String): CommandLineOptions {
    val optionParser = OptionParser()
    val configFileArg = optionParser
            .accepts("config-file", "The path to the config file")
            .withRequiredArg()
            .describedAs("filepath")
    val updateNetworkParametersArg = optionParser
            .accepts("update-network-parameters", "Update network parameters filepath. Currently only network parameters initialisation is supported.")
            .withRequiredArg()
            .describedAs("The new network map")
            .describedAs("filepath")
    val modeArg = optionParser
            .accepts("mode", "Set the mode of this application")
            .withRequiredArg()
            .withValuesConvertedBy(object : EnumConverter<DoormanParameters.Mode>(DoormanParameters.Mode::class.java) {})
            .defaultsTo(DoormanParameters.Mode.DOORMAN)
    val helpOption = optionParser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp()

    val optionSet = optionParser.parse(*args)
    // Print help and exit on help option or if there are missing options.
    if (optionSet.has(helpOption) || !optionSet.has(configFileArg)) {
        throw ShowHelpException(optionParser)
    }

    val configFile = Paths.get(optionSet.valueOf(configFileArg)).toAbsolutePath()
    val updateNetworkParametersOptionValue = optionSet.valueOf(updateNetworkParametersArg)
    val updateNetworkParameters = updateNetworkParametersOptionValue?.let {
        Paths.get(it).toAbsolutePath()
    }

    return CommandLineOptions(configFile, updateNetworkParameters, optionSet.valueOf(modeArg))
}

/**
 * Parses a configuration file, which contains all the configuration except the initial values for the network
 * parameters.
 */
fun parseParameters(configFile: Path, overrides: Config = ConfigFactory.empty()): DoormanParameters {
    val config = ConfigFactory
            .parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))
            .resolve()
    return overrides
            .withFallback(config)
            .parseAs()
}
