/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.internal.exists
import net.corda.core.internal.readObject
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.config.parseAs
import java.nio.file.Path
import java.time.Instant

/**
 * Data class representing a [NotaryInfo] which can be easily parsed by a typesafe [ConfigFactory].
 * @property notaryNodeInfoFile path to the node info file of the notary node.
 * @property validating whether the notary is validating
 */
data class NotaryConfig(private val notaryNodeInfoFile: Path,
                        private val validating: Boolean) {
    fun toNotaryInfo(): NotaryInfo {
        val nodeInfo = notaryNodeInfoFile.readObject<SignedNodeInfo>().verified()
        // It is always the last identity (in the list of identities) that corresponds to the notary identity.
        // In case of a single notary, the list has only one element. In case of distributed notaries the list has
        // two items and the second one corresponds to the notary identity.
        return NotaryInfo(nodeInfo.legalIdentities.last(), validating)
    }
}

/**
 * Data class containing the fields from [NetworkParameters] which can be read at start-up time from doorman.
 * It is a proper subset of [NetworkParameters] except for the [notaries] field which is replaced by a list of
 * [NotaryConfig] which is parsable.
 *
 * This is public only because [parseAs] needs to be able to call its constructor.
 */
data class NetworkParametersConfig(val minimumPlatformVersion: Int,
                                   val notaries: List<NotaryConfig>,
                                   val maxMessageSize: Int,
                                   val maxTransactionSize: Int) {
    fun toNetworkParameters(modifiedTime: Instant, epoch: Int): NetworkParameters {
        return NetworkParameters(
                minimumPlatformVersion,
                notaries.map { it.toNotaryInfo() },
                maxMessageSize,
                maxTransactionSize,
                modifiedTime,
                epoch,
                // TODO: Tudor, Michal - pass the actual network parameters where we figure out how
                emptyMap()
        )
    }
}

fun parseNetworkParametersConfig(configFile: Path): NetworkParametersConfig {
    check(configFile.exists()) { "File $configFile does not exist" }
    return ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults()).parseAs()
}
