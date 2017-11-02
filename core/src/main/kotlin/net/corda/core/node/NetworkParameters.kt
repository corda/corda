package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Duration
import java.time.Instant

/**
 * @property minimumPlatformVersion
 * @property notaries
 * @property eventHorizon
 * @property maxMessageSize Maximum P2P message sent over the wire in bytes.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime
 * @property epoch Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 */
// TODO Wire up the parameters
@CordaSerializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val eventHorizon: Duration,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        val modifiedTime: Instant,
        val epoch: Int
) {
    init {
        require(minimumPlatformVersion > 0) { "minimumPlatformVersion must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "epoch must be at least 1" }
    }
}

/**
 *
 */
@CordaSerializable
data class NotaryInfo(val identity: Party, val validating: Boolean)
