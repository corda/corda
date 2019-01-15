package net.corda.testing.node

import net.corda.core.DoNotImplement
import net.corda.core.identity.CordaX500Name
import net.corda.testing.driver.VerifierType

/**
 * A notary spec for describing a notary which will be started automatically by the driver and which will be part of
 * the network parameters used by all the nodes.
 *
 * @property name The name of the notary. If this is a notary cluster then each node member will be assigned a name based on this name.
 * @property validating Boolean for whether the notary is validating or non-validating.
 * @property rpcUsers A list of users able to instigate RPC for this node or cluster of nodes.
 * @property verifierType How the notary will verify transactions.
 * @property cluster [ClusterSpec] if this is a distributed cluster notary. If null then this is a single-node notary.
 */
data class NotarySpec(
        val name: CordaX500Name,
        val validating: Boolean = true,
        val rpcUsers: List<User> = emptyList(),
        val verifierType: VerifierType = VerifierType.InMemory,
        val cluster: ClusterSpec? = null
) {
    // These extra fields are handled this way to preserve Kotlin wire compatibility wrt additional parameters with default values.
    constructor(name: CordaX500Name,
                validating: Boolean = true,
                rpcUsers: List<User> = emptyList(),
                verifierType: VerifierType = VerifierType.InMemory,
                cluster: ClusterSpec? = null,
                maximumHeapSize: String = "512m"): this(name, validating, rpcUsers, verifierType, cluster) {
        this.maximumHeapSize = maximumHeapSize
    }

    var maximumHeapSize: String = "512m"
}

/**
 * Abstract class specifying information about the consensus algorithm used for a cluster of nodes.
 */
@DoNotImplement
abstract class ClusterSpec {
    /** The number of nodes within the cluster. **/
    abstract val clusterSize: Int

    /** A class representing the configuration of a raft consensus algorithm used for a cluster of nodes. **/
    data class Raft(
            override val clusterSize: Int
    ) : ClusterSpec() {
        init {
            require(clusterSize > 0)
        }
    }
}