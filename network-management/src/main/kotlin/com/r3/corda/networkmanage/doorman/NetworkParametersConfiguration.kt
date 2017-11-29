package com.r3.corda.networkmanage.doorman

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.exists
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.days
import net.corda.core.utilities.parsePublicKeyBase58
import net.corda.nodeapi.config.parseAs
import java.nio.file.Path
import java.time.Instant

/**
 * Initial value for [NetworkParameters.epoch].
 */
private const val DEFAULT_EPOCH = 1

/**
 * Data class representing a [NotaryInfo] which can be easily parsed by a typesafe [ConfigFactory].
 * @property name the X500Name of the notary.
 * @property key the public key as serialized by [toBase58String]
 * @property validating whether the notary is validating
 */
internal data class NotaryConfiguration(private val name: CordaX500Name,
                                        private val key: String,
                                        private val validating: Boolean) {
    fun toNotaryInfo(): NotaryInfo = NotaryInfo(Party(name, parsePublicKeyBase58(key)), validating)
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
                                                   val eventHorizonDays: Int,
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
    check(configFile.exists()) { "File $configFile does not exist" }
    val networkParametersConfig = ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults())
            .parseAs(NetworkParametersConfiguration::class)

    return NetworkParameters(networkParametersConfig.minimumPlatformVersion,
            networkParametersConfig.notaries.map { it.toNotaryInfo() },
            networkParametersConfig.eventHorizonDays.days,
            networkParametersConfig.maxMessageSize,
            networkParametersConfig.maxTransactionSize,
            Instant.now(),
            epoch)
}