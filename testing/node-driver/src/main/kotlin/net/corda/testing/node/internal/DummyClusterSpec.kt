package net.corda.testing.node.internal

import net.corda.testing.node.ClusterSpec

/**
 * Only used for testing the notary communication path. Can be configured to act as a Raft (singular identity),
 * or a BFT (composite key identity) notary service.
 */
data class DummyClusterSpec(
        override val clusterSize: Int,
        /**
         * If *true*, the cluster will use a shared composite public key for the service identity, with individual
         * private keys. If *false*, the same "singular" key pair will be shared by all replicas.
         */
        val compositeServiceIdentity: Boolean = false
) : ClusterSpec() {
    init {
        require(clusterSize > 0)
    }
}