package net.corda.testing.node

import net.corda.core.identity.CordaX500Name
import net.corda.node.services.config.VerifierType
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.nodeapi.internal.config.User

data class NotarySpec(
        val name: CordaX500Name,
        val validating: Boolean = true,
        val rpcUsers: List<User> = emptyList(),
        val verifierType: VerifierType = VerifierType.InMemory,
        val cluster: ClusterSpec? = null
) {
    init {
        // TODO This will be removed once network parameters define the notaries
        when (cluster) {
            is ClusterSpec.Raft -> require(name.commonName == RaftValidatingNotaryService.id)
            null -> require(name.commonName == null)
        }
    }
}

sealed class ClusterSpec {
    abstract val clusterSize: Int

    data class Raft(override val clusterSize: Int) : ClusterSpec() {
        init {
            require(clusterSize > 0)
        }
    }
}