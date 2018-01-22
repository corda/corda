package net.corda.core.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

const val NETWORK_PARAMS_FILE_NAME = "network-parameters"
const val NETWORK_PARAMS_UPDATE_FILE_NAME = "network-parameters-update"

/**
 * Data class containing hash of [NetworkParameters] and network participant's [NodeInfo] hashes.
 * @property parametersUpdate if present means that network operator scheduled update of network parameters
 */
@CordaSerializable
data class NetworkMap(
        val nodeInfoHashes: List<SecureHash>,
        val networkParameterHash: SecureHash,
        val parametersUpdate: ParametersUpdate?
)

/**
 * @property newParametersHash Hash of the new [NetworkParameters], which will be requested from network map
 * @property description Short description of the update
 * @property flagDay initially set to null, when most of the participants of the network agree on the new parameters the network
 * operator decides when to switch to using new parameters
 */
@CordaSerializable
data class ParametersUpdate(
        val newParametersHash: SecureHash,
        val description: String,
        val flagDay: Instant?
)

/**
 * @property minimumPlatformVersion Minimum version of Corda platform that is required for nodes in the network.
 * @property notaries List of well known and trusted notary identities with information on validation type.
 * @property maxMessageSize Maximum P2P message sent over the wire in bytes.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime
 * @property epoch Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 */
// TODO Add eventHorizon - how many days a node can be offline before being automatically ejected from the network.
//  It needs separate design.
// TODO Currently maxTransactionSize is not wired.
@CordaSerializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        val modifiedTime: Instant,
        val epoch: Int
) {
    init {
        require(minimumPlatformVersion > 0) { "minimumPlatformVersion must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "epoch must be at least 1" }
        require(maxMessageSize > 0) { "maxMessageSize must be at least 1" }
        require(maxTransactionSize > 0) { "maxTransactionSize must be at least 1" }
    }
}

@CordaSerializable
data class NotaryInfo(val identity: Party, val validating: Boolean)
