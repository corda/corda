package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.toConfigWithOptions
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.div
import net.corda.core.internal.isRegularFile
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.config.parseAs
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

data class NetworkManagementServerParameters(// TODO: Move local signing to signing server.
        val host: String,
        val port: Int,
        val dataSourceProperties: Properties,
        val databaseProperties: Properties? = null,
        val mode: Mode,

        val doormanConfig: DoormanConfig?,
        val networkMapConfig: NetworkMapConfig?,

        val updateNetworkParameters: Path?,

        // TODO Should be part of a localSigning sub-config
        val keystorePath: Path? = null,
        // TODO Should be part of a localSigning sub-config
        val rootStorePath: Path? = null,
        val keystorePassword: String?,
        // TODO Should be part of a localSigning sub-config
        val caPrivateKeyPassword: String?,
        // TODO Should be part of a localSigning sub-config
        val rootKeystorePassword: String?,
        // TODO Should be part of a localSigning sub-config
        val rootPrivateKeyPassword: String?

) {
    companion object {
        // TODO: Do we really need these defaults?
        val DEFAULT_APPROVE_INTERVAL = 5.seconds
        val DEFAULT_SIGN_INTERVAL = 5.seconds
    }

    init {
        if (updateNetworkParameters != null) {
            check(updateNetworkParameters.isRegularFile()) { "Update network parameters file $updateNetworkParameters does not exist" }
        }
    }
}

data class DoormanConfig(val approveAll: Boolean = false,
                         val jiraConfig: JiraConfig? = null,
                         val approveInterval: Long = NetworkManagementServerParameters.DEFAULT_APPROVE_INTERVAL.toMillis())

data class NetworkMapConfig(val cacheTimeout: Long,
        // TODO: Move signing to signing server.
                            val signInterval: Long = NetworkManagementServerParameters.DEFAULT_SIGN_INTERVAL.toMillis())

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

/**
 * Parses the doorman command line options.
 */
fun parseParameters(vararg args: String): NetworkManagementServerParameters {
    val argConfig = args.toConfigWithOptions {
        accepts("config-file", "The path to the config file")
                .withRequiredArg()
                .describedAs("filepath")
        accepts("update-network-parameters", "Update network parameters filepath. Currently only network parameters initialisation is supported.")
                .withRequiredArg()
                .describedAs("The new network map")
                .describedAs("filepath")
        accepts("mode", "Set the mode of this application")
                .withRequiredArg()
                .defaultsTo(Mode.DOORMAN.name)
    }

    val configFile = if (argConfig.hasPath("configFile")) {
        Paths.get(argConfig.getString("configFile"))
    } else {
        Paths.get(".") / "network-management.conf"
    }
    check(configFile.isRegularFile()) { "Config file $configFile does not exist" }

    return argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true)))
            .resolve()
            .parseAs()
}

