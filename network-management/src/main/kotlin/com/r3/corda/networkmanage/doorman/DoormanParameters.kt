package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.common.utils.toConfigWithOptions
import com.r3.corda.networkmanage.doorman.DoormanParameters.Companion.DEFAULT_APPROVE_INTERVAL
import com.r3.corda.networkmanage.doorman.DoormanParameters.Companion.DEFAULT_SIGN_INTERVAL
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.OptionParser
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.exists
import net.corda.core.internal.div
import net.corda.core.utilities.seconds
import net.corda.nodeapi.config.parseAs
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*

data class DoormanParameters(val keystorePassword: String?,
                             val caPrivateKeyPassword: String?,
                             val rootKeystorePassword: String?,
                             val rootPrivateKeyPassword: String?,
                             val host: String,
                             val port: Int,
                             val dataSourceProperties: Properties,
                             val mode: Mode = Mode.DOORMAN,
                             val approveAll: Boolean = false,
                             val databaseProperties: Properties? = null,
                             val jiraConfig: JiraConfig? = null,
                             val keystorePath: Path? = null,
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
                              val initialNetworkParameters: Path) {
    init {
        check(configFile.isRegularFile()) { "Config file $configFile does not exist" }
        check(initialNetworkParameters.isRegularFile()) { "Initial network parameters file $initialNetworkParameters does not exist" }
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
    val initialNetworkParametersArg = optionParser
            .accepts("initial-network-parameters", "initial network parameters filepath")
            .withRequiredArg()
            .describedAs("The initial network map")
            .describedAs("filepath")
    val helpOption = optionParser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp();

    val optionSet = optionParser.parse(*args)
    // Print help and exit on help option.
    if (optionSet.has(helpOption)) {
        throw ShowHelpException(optionParser)
    }
    val configFile = Paths.get(optionSet.valueOf(configFileArg)).toAbsolutePath()
    val initialNetworkParameters = Paths.get(optionSet.valueOf(initialNetworkParametersArg)).toAbsolutePath()

    return CommandLineOptions(configFile, initialNetworkParameters)
}

/**
 * Parses a configuration file, which contains all the configuration except the initial values for the network
 * parameters.
 */
fun parseParameters(configFile: Path): DoormanParameters {
    return ConfigFactory
            .parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))
            .resolve()
            .parseAs()
}
