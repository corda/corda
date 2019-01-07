package net.corda.notary.experimental.raft

import net.corda.core.utilities.NetworkHostAndPort

/** Configuration properties specific to the RaftNotaryService. */
data class RaftConfig(
        /**
         * The host and port to which to bind the embedded Raft server. Note that the Raft cluster uses a
         * separate transport layer for communication that does not integrate with ArtemisMQ messaging services.
         */
        val nodeAddress: NetworkHostAndPort,
        /**
         * Must list the addresses of all the members in the cluster. At least one of the members mustbe active and
         * be able to communicate with the cluster leader for the node to join the cluster. If empty, a new cluster
         * will be bootstrapped.
         */
        val clusterAddresses: List<NetworkHostAndPort>
)