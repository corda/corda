package com.r3.corda.networkmanage.doorman

import com.google.common.primitives.Booleans
import com.r3.corda.networkmanage.common.utils.ArgsParser
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.OptionSet
import joptsimple.util.EnumConverter
import joptsimple.util.PathConverter
import joptsimple.util.PathProperties
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.nodeapi.internal.config.parseAs
import java.nio.file.Path
import java.time.Instant

class DoormanArgsParser : ArgsParser<DoormanCmdLineOptions>() {
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
    private val setNetworkParametersArg = optionParser
            .accepts("set-network-parameters", "Set the network parameters using the given file. This can be for either the initial set or to schedule an update.")
            .withRequiredArg()
            .withValuesConvertedBy(PathConverter(PathProperties.FILE_EXISTING))
    private val flagDayArg = optionParser.accepts("flag-day", "Roll over the scheduled network parameters to be the current.")
    private val cancelUpdateArg = optionParser.accepts("cancel-update", "Cancel the scheduled update of the network parameters.")
    private val trustStorePasswordArg = optionParser
            .accepts("trust-store-password", "Password for the generated network root trust store. Only applicable when operating in ${Mode.ROOT_KEYGEN} mode.")
            .withRequiredArg()

    override fun parse(optionSet: OptionSet): DoormanCmdLineOptions {
        val configFile = optionSet.valueOf(configFileArg)
        val mode = optionSet.valueOf(modeArg)
        val setNetworkParametersFile = optionSet.valueOf(setNetworkParametersArg)
        val flagDay = optionSet.has(flagDayArg)
        val cancelUpdate = optionSet.has(cancelUpdateArg)
        require(Booleans.countTrue(setNetworkParametersFile != null, flagDay, cancelUpdate) <= 1) {
            "Only one of $setNetworkParametersArg, $flagDay and $cancelUpdate can be specified"
        }
        val networkParametersOption = when {
            setNetworkParametersFile != null -> NetworkParametersCmd.Set.fromFile(setNetworkParametersFile)
            flagDay -> NetworkParametersCmd.FlagDay
            cancelUpdate -> NetworkParametersCmd.CancelUpdate
            else -> null
        }
        val trustStorePassword = optionSet.valueOf(trustStorePasswordArg)
        return DoormanCmdLineOptions(configFile, mode, networkParametersOption, trustStorePassword)
    }
}

data class DoormanCmdLineOptions(val configFile: Path,
                                 val mode: Mode,
                                 val networkParametersCmd: NetworkParametersCmd?,
                                 val trustStorePassword: String?) {
    init {
        // Make sure trust store password is only specified in root keygen mode.
        if (mode != Mode.ROOT_KEYGEN) {
            require(trustStorePassword == null) { "Trust store password should not be specified in this mode." }
        }
    }
}

sealed class NetworkParametersCmd {
    /**
     * This is the same as [NetworkParametersConfig] but instead of using [NotaryConfig] it uses the simpler to test with
     * [NotaryInfo].
     */
    data class Set(val minimumPlatformVersion: Int,
                   val notaries: List<NotaryInfo>,
                   val maxMessageSize: Int,
                   val maxTransactionSize: Int,
                   val parametersUpdate: ParametersUpdateConfig?) : NetworkParametersCmd() {
        companion object {
            fun fromFile(file: Path): Set {
                return ConfigFactory.parseFile(file.toFile(), ConfigParseOptions.defaults())
                        .parseAs<NetworkParametersConfig>()
                        .let {
                            Set(
                                    it.minimumPlatformVersion,
                                    it.notaries.map { it.toNotaryInfo() },
                                    it.maxMessageSize,
                                    it.maxTransactionSize,
                                    it.parametersUpdate
                            )
                        }
            }
        }

        fun checkCompatibility(currentNetParams: NetworkParameters) {
            // TODO Comment it out when maxMessageSize is properly wired
//        require(previousParameters.maxMessageSize <= newParameters.maxMessageSize) { "maxMessageSize can only increase" }
            require(maxTransactionSize >= currentNetParams.maxTransactionSize) { "maxTransactionSize can only increase" }
            val removedNames = currentNetParams.notaries.map { it.identity.name } - notaries.map { it.identity.name }
            require(removedNames.isEmpty()) { "notaries cannot be removed: $removedNames" }
            val removedKeys = currentNetParams.notaries.map { it.identity.owningKey } - notaries.map { it.identity.owningKey }
            require(removedKeys.isEmpty()) { "notaries cannot be removed: $removedKeys" }
        }

        fun toNetworkParameters(modifiedTime: Instant, epoch: Int): NetworkParameters {
            return NetworkParameters(
                    minimumPlatformVersion,
                    notaries,
                    maxMessageSize,
                    maxTransactionSize,
                    modifiedTime,
                    epoch,
                    // TODO: Tudor, Michal - pass the actual network parameters where we figure out how
                    emptyMap()
            )
        }
    }

    object FlagDay : NetworkParametersCmd()

    object CancelUpdate : NetworkParametersCmd()
}
