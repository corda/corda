package net.corda.core.node

import net.corda.annotations.serialization.Serializable
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
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
 */
// TODO Add eventHorizon - how many days a node can be offline before being automatically ejected from the network.
//  It needs separate design.
@Serializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        val modifiedTime: Instant,
        val epoch: Int,
        val whitelistedContractImplementations: Map<String, List<AttachmentId>>
) {
    init {
        require(minimumPlatformVersion > 0) { "minimumPlatformVersion must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "epoch must be at least 1" }
        require(maxMessageSize > 0) { "maxMessageSize must be at least 1" }
        require(maxTransactionSize > 0) { "maxTransactionSize must be at least 1" }
    }
}

/**
 * Data class storing information about notaries available in the network.
 * @property identity Identity of the notary (note that it can be an identity of the distributed node).
 * @property validating Indicates if the notary is validating.
 */
@Serializable
data class NotaryInfo(val identity: Party, val validating: Boolean)
