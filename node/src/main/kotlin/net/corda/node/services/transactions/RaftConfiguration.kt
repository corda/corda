package net.corda.node.services.transactions

import com.google.common.net.HostAndPort

// TODO: Move raft notary specific configuration out of FullNodeConfiguration
class RaftConfiguration(config: net.corda.node.services.config.FullNodeConfiguration) {
    val notaryNodeAddress: HostAndPort = config.notaryNodeAddress!!
    val notaryClusterAddresses: List<HostAndPort> = config.notaryClusterAddresses
}