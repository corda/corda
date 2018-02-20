package net.corda.testing.node

import net.corda.core.DoNotImplement
import net.corda.core.identity.CordaX500Name
import net.corda.node.services.config.VerifierType

/**
 * A data class representing parameters describing a Notary
 *
 * @property name A [CordaX500Name] representing legal name of this node
 * @property validating If set to true, this node validates any transactions and their dependencies sent to it.
 * @property rpcUsers A list of users able to instigate RPC for this node.
 * @property verifierType How the notary will verify transactions.
 * @property cluster Information about the consensus algorithm used if this notary is part of a cluster.
 */
data class NotarySpec(
        val name: CordaX500Name,
        val validating: Boolean = true,
        val rpcUsers: List<User> = emptyList(),
        val verifierType: VerifierType = VerifierType.InMemory,
        val cluster: ClusterSpec? = null
)


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