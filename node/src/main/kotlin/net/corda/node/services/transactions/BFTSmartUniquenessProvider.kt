package net.corda.node.services.transactions

import com.google.common.net.HostAndPort
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.utilities.loggerFor
import org.jetbrains.exposed.sql.Database
import kotlin.concurrent.thread

/**
 * A [UniquenessProvider] based on the [bft-smart library](https://github.com/bft-smart/library).
 *
 * Experimental, not ready for production yet.
 *
 * A [BFTSmartUniquenessProvider] starts a [BFTSmartServer] that joins the notary cluster and stores committed input
 * states and a [BFTSmartClient] to commit states to the notary cluster.
 *
 * @param clusterAddresses the addresses of all BFTSmartUniquenessProviders of the notary cluster
 * @param myAddress the address of this uniqueness provider, must be listed in clusterAddresses
 */
class BFTSmartUniquenessProvider(val myAddress: HostAndPort, val clusterAddresses: List<HostAndPort>, val db: Database) : UniquenessProvider {
    // TODO: Write bft-smart host config file based on Corda node configuration.
    // TODO: Define and document the configuration of the bft-smart cluster.

    // TODO: Potentially update the bft-smart API for our use case or rebuild client and server from lower level building
    // blocks bft-smart provides.

    // TODO: Support cluster membership changes. This requires reading about reconfiguration of bft-smart clusters and
    // perhaps a design doc. In general, it seems possible to use the state machine to reconfigure the cluster (reaching
    // consensus about  membership changes). Nodes that join the cluster for the first time or re-join can go through
    // a "recovering" state and request missing data from their peers.

    init {
        require(myAddress in clusterAddresses) {
            "expected myAddress '$myAddress' to be listed in clusterAddresses '$clusterAddresses'"
        }
        startServerThread()
    }

    companion object {
        private val log = loggerFor<BFTSmartUniquenessProvider>()
    }

    private val bftClient = BFTSmartClient<StateRef, UniquenessProvider.ConsumingTx>(clientId())

    /** Throws UniquenessException if conflict is detected */
    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {
        val entries = states.mapIndexed { i, stateRef ->
            stateRef to UniquenessProvider.ConsumingTx(txId, i, callerIdentity)
        }.toMap()
        val conflicts = bftClient.put(entries)
        if (conflicts.isNotEmpty()) {
            throw UniquenessException(UniquenessProvider.Conflict(conflicts))
        }
        log.debug("All input states of transaction $txId have been committed")
    }

    private fun serverId(): Int {
        return clusterAddresses.indexOf(myAddress)
    }

    private fun clientId(): Int {
        // 10k IDs are reserved for servers.
        require(clusterAddresses.size <= 10000)
        return 10000 + serverId()
    }

    private fun startServerThread() {
        val id = serverId()
        thread(name="BFTSmartServer-$id", isDaemon=true) {
            BFTSmartServer<StateRef, UniquenessProvider.ConsumingTx>(id, db, "bft_smart_notary_committed_states")
        }
    }
}
