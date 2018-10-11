package net.corda.notary.raft

import net.corda.core.utilities.NetworkHostAndPort

/** Configuration properties specific to the RaftNotaryService. */
data class RaftConfig(
        /** The advertised address of the current Raft node. */
        val nodeAddress: NetworkHostAndPort,
        /** A list of Raft cluster member addresses to connect to. */
        val clusterAddresses: List<NetworkHostAndPort>
)