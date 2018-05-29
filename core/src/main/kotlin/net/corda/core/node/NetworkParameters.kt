/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.utilities.days
import java.time.Duration
import java.time.Instant

/**
 * Network parameters are a set of values that every node participating in the zone needs to agree on and use to
 * correctly interoperate with each other.
 * @property minimumPlatformVersion Minimum version of Corda platform that is required for nodes in the network.
 * @property notaries List of well known and trusted notary identities with information on validation type.
 * @property maxMessageSize This is currently ignored. However, it will be wired up in a future release.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime Last modification time of network parameters set.
 * @property epoch Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 * @property whitelistedContractImplementations List of whitelisted jars containing contract code for each contract class.
 *  This will be used by [net.corda.core.contracts.WhitelistedByZoneAttachmentConstraint]. Read more about contract constraints here: <https://docs.corda.net/api-contract-constraints.html>
 * @property eventHorizon Time after which nodes will be removed from the network map if they have not been seen
 * during this period
 */
@CordaSerializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        val modifiedTime: Instant,
        val epoch: Int,
        val whitelistedContractImplementations: Map<String, List<AttachmentId>>,
        val eventHorizon: Duration
) {
    @DeprecatedConstructorForDeserialization(1)
    constructor (minimumPlatformVersion: Int,
                 notaries: List<NotaryInfo>,
                 maxMessageSize: Int,
                 maxTransactionSize: Int,
                 modifiedTime: Instant,
                 epoch: Int,
                 whitelistedContractImplementations: Map<String, List<AttachmentId>>
    ) : this(minimumPlatformVersion,
            notaries,
            maxMessageSize,
            maxTransactionSize,
            modifiedTime,
            epoch,
            whitelistedContractImplementations,
            Int.MAX_VALUE.days
    )

    init {
        require(minimumPlatformVersion > 0) { "minimumPlatformVersion must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "epoch must be at least 1" }
        require(maxMessageSize > 0) { "maxMessageSize must be at least 1" }
        require(maxTransactionSize > 0) { "maxTransactionSize must be at least 1" }
        require(!eventHorizon.isNegative) { "eventHorizon must be positive value" }
    }

    fun copy(minimumPlatformVersion: Int,
             notaries: List<NotaryInfo>,
             maxMessageSize: Int,
             maxTransactionSize: Int,
             modifiedTime: Instant,
             epoch: Int,
             whitelistedContractImplementations: Map<String, List<AttachmentId>>
    ): NetworkParameters {
        return copy(minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                modifiedTime = modifiedTime,
                epoch = epoch,
                whitelistedContractImplementations = whitelistedContractImplementations,
                eventHorizon = eventHorizon)
    }

    override fun toString(): String {
        return """NetworkParameters {
  minimumPlatformVersion=$minimumPlatformVersion
  notaries=$notaries
  maxMessageSize=$maxMessageSize
  maxTransactionSize=$maxTransactionSize
  whitelistedContractImplementations {
    ${whitelistedContractImplementations.entries.joinToString("\n    ")}
  }
  eventHorizon=$eventHorizon
  modifiedTime=$modifiedTime
  epoch=$epoch
}"""
    }
}

/**
 * Data class storing information about notaries available in the network.
 * @property identity Identity of the notary (note that it can be an identity of the distributed node).
 * @property validating Indicates if the notary is validating.
 */
@CordaSerializable
data class NotaryInfo(val identity: Party, val validating: Boolean)
