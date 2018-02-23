package com.r3.corda.networkmanage.doorman

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.exists
import net.corda.core.internal.readAll
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.config.parseAs
import java.nio.file.Path
import java.time.Instant

/**
 * Initial value for [NetworkParameters.epoch].
 */
private const val DEFAULT_EPOCH = 1

/**
 * Data class representing a [NotaryInfo] which can be easily parsed by a typesafe [ConfigFactory].
 * @property notaryNodeInfoFile path to the node info file of the notary node.
 * @property validating whether the notary is validating
 */
internal data class NotaryConfiguration(private val notaryNodeInfoFile: Path,
                                        private val validating: Boolean) {
    fun toNotaryInfo(): NotaryInfo {
        val nodeInfo = notaryNodeInfoFile.readAll().deserialize<SignedNodeInfo>().verified()
        // It is always the last identity (in the list of identities) that corresponds to the notary identity.
        // In case of a single notary, the list has only one element. In case of distributed notaries the list has
        // two items and the second one corresponds to the notary identity.
        return NotaryInfo(nodeInfo.legalIdentities.last(), validating)
    }
}

/**
 * data class containing the fields from [NetworkParameters] which can be read at start-up time from doorman.
 * It is a proper subset of [NetworkParameters] except for the [notaries] field which is replaced by a list of
 * [NotaryConfiguration] which is parsable.
 *
 * This is public only because [parseAs] needs to be able to call its constructor.
 */
internal data class NetworkParametersConfiguration(val minimumPlatformVersion: Int,
                                                   val notaries: List<NotaryConfiguration>,
                                                   val maxMessageSize: Int,
                                                   val maxTransactionSize: Int)

/**
 * Parses a file and returns a [NetworkParameters] instance.
 *
 * @return a [NetworkParameters] with values read from [configFile] except:
 * an epoch of [DEFAULT_EPOCH] and
 * a modifiedTime initialized with [Instant.now].
 */
fun parseNetworkParametersFrom(configFile: Path, epoch: Int = DEFAULT_EPOCH): NetworkParameters {
    return parseNetworkParameters(parseNetworkParametersConfigurationFrom(configFile), epoch)
}

internal fun parseNetworkParametersConfigurationFrom(configFile: Path): NetworkParametersConfiguration {
    check(configFile.exists()) { "File $configFile does not exist" }
    return ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults())
            .parseAs(NetworkParametersConfiguration::class)
}

internal fun parseNetworkParameters(configuration: NetworkParametersConfiguration, epoch: Int = DEFAULT_EPOCH): NetworkParameters {
    return NetworkParameters(configuration.minimumPlatformVersion,
            configuration.notaries.map { it.toNotaryInfo() },
            configuration.maxMessageSize,
            configuration.maxTransactionSize,
            Instant.now(),
            epoch,
            // TODO: Tudor, Michal - pass the actual network parameters where we figure out how
            emptyMap())
}